package com.scott.payment.data.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import com.scott.payment.data.entity.DataMerchantNotificationRetryOutboxDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationLogMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationRetryOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationAttemptCommitService
 * @date : 2026-08-20 23:30
 * @email : scott_x@163.com
 * @description : 在同一交易分片事务中原子提交商户回调尝试日志、任务结果和自动重试 Outbox
 * @status : create
 */
@Slf4j
@Service
public class MerchantNotificationAttemptCommitService {

    /** 可继续自动重试的任务状态。 */
    private static final String STATUS_FAILED = "FAILED";
    /** 自动或人工回调不再继续重试的任务状态。 */
    private static final String STATUS_CLOSED = "CLOSED";
    /** 交易分表和绝对投递时间统一使用的平台时区。 */
    private static final String PLATFORM_ZONE_ID = "Asia/Shanghai";
    /** 自动重试 Outbox 自身允许的最大 MQ 发布次数。 */
    private static final int OUTBOX_MAX_RETRY_COUNT = 8;

    /** 通知任务状态 Mapper。 */
    private final DataMerchantNotificationMapper notificationMapper;
    /** 通知尝试日志 Mapper。 */
    private final DataMerchantNotificationLogMapper notificationLogMapper;
    /** 同分片交易 Outbox Mapper。 */
    private final DataMerchantNotificationRetryOutboxMapper outboxMapper;

    /**
     * 创建回调结果事务提交服务。
     *
     * @param notificationMapper 通知任务状态 Mapper
     * @param notificationLogMapper 通知尝试日志 Mapper
     * @param outboxMapper 自动重试 Outbox Mapper
     */
    public MerchantNotificationAttemptCommitService(
            DataMerchantNotificationMapper notificationMapper,
            DataMerchantNotificationLogMapper notificationLogMapper,
            DataMerchantNotificationRetryOutboxMapper outboxMapper) {
        this.notificationMapper = notificationMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.outboxMapper = outboxMapper;
    }

    /**
     * 查询人工回调事件是否已经完成数据库提交。
     *
     * @param callbackEventId 人工 MQ 事件号
     * @param transactionDateTime 交易分片时间
     * @return 已提交时返回成功标记 0/1，未提交返回 null
     */
    @DS(DataSourceName.TRANSACTION)
    public Integer findManualOutcome(String callbackEventId, LocalDateTime transactionDateTime) {
        return notificationLogMapper.selectManualOutcome(callbackEventId, transactionDateTime);
    }

    /**
     * 原子写入成功尝试日志并把任务推进为 SUCCESS。
     *
     * @param task 当前通知任务快照
     * @param processingVersion PROCESSING 状态版本
     * @param attemptLog 本次脱敏尝试日志
     * @param finishedTime HTTP 完成时间
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void recordSuccess(DataMerchantNotificationTaskDO task,
                              int processingVersion,
                              DataMerchantNotificationLogDO attemptLog,
                              LocalDateTime finishedTime) {
        insertAttemptLog(attemptLog);
        int affectedRows = notificationMapper.markSuccess(
                task.getId(), task.getTransactionDateTime(), processingVersion, finishedTime);
        requireSingleStateUpdate(affectedRows, task, "SUCCESS", processingVersion);
    }

    /**
     * 原子写入失败尝试日志、推进任务状态，并在需要时创建下一次自动重试 Outbox。
     *
     * @param task 当前通知任务快照
     * @param processingVersion PROCESSING 状态版本
     * @param nextStatus FAILED 或 CLOSED
     * @param nextRetryTime 下次回调时间，CLOSED 时为空
     * @param failReason 脱敏失败摘要
     * @param finishedTime 当前尝试结束时间
     * @param currentAttemptNo 当前已完成的尝试序号
     * @param attemptLog 本次脱敏尝试日志
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(DataMerchantNotificationTaskDO task,
                              int processingVersion,
                              String nextStatus,
                              LocalDateTime nextRetryTime,
                              String failReason,
                              LocalDateTime finishedTime,
                              int currentAttemptNo,
                              DataMerchantNotificationLogDO attemptLog) {
        if (!STATUS_FAILED.equals(nextStatus) && !STATUS_CLOSED.equals(nextStatus)) {
            throw new IllegalArgumentException("merchant notification failure status is invalid");
        }
        insertAttemptLog(attemptLog);
        int affectedRows = notificationMapper.markFailed(
                task.getId(), task.getTransactionDateTime(), processingVersion, nextStatus,
                nextRetryTime, failReason, finishedTime);
        requireSingleStateUpdate(affectedRows, task, nextStatus, processingVersion);
        if (STATUS_FAILED.equals(nextStatus)) {
            if (nextRetryTime == null) {
                throw new IllegalArgumentException("merchant notification next retry time is required");
            }
            int failedVersion = processingVersion + 1;
            if (outboxMapper.insert(buildOutbox(task, failedVersion, currentAttemptNo + 1,
                    nextRetryTime, finishedTime)) != 1) {
                log.error("event: DATA_MERCHANT_NOTIFY_RETRY_OUTBOX_INSERT_FAILED traceId: {} notifyId: {} transactionId: {} expectedVersion: {}",
                        TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), failedVersion);
                throw new IllegalStateException("merchant notification retry outbox was not inserted");
            }
        }
    }

    /** 写入单次尝试日志，失败时使当前结果事务整体回滚。 */
    private void insertAttemptLog(DataMerchantNotificationLogDO attemptLog) {
        if (notificationLogMapper.insert(attemptLog) != 1) {
            throw new IllegalStateException("merchant notification attempt log was not inserted");
        }
    }

    /** 要求任务状态 CAS 恰好更新一行。 */
    private void requireSingleStateUpdate(int affectedRows,
                                          DataMerchantNotificationTaskDO task,
                                          String targetStatus,
                                          int expectedVersion) {
        if (affectedRows != 1) {
            log.error("event: DATA_MERCHANT_NOTIFY_STATE_CONFLICT traceId: {} notifyId: {} transactionId: {} merchantId: {} targetStatus: {} expectedVersion: {} affectedRows: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    targetStatus, expectedVersion, affectedRows);
            throw new IllegalStateException("merchant notification state transition conflict");
        }
    }

    /** 构造不包含商户 HTTP 协议数据的交易 Outbox 快照。 */
    private DataMerchantNotificationRetryOutboxDO buildOutbox(DataMerchantNotificationTaskDO task,
                                                               int expectedVersion,
                                                               int nextAttemptNo,
                                                               LocalDateTime deliverAt,
                                                               LocalDateTime now) {
        String eventNo = eventNo(task.getNotifyId(), expectedVersion);
        MerchantNotificationRetryDueMessage message = new MerchantNotificationRetryDueMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(now);
        message.setTraceId(TraceContext.getOrCreateTraceId());
        message.setRetryCount(0);
        message.setNotifyId(task.getNotifyId());
        message.setTransactionId(task.getTransactionId());
        message.setTransactionDateTime(task.getTransactionDateTime());
        message.setExpectedVersion(expectedVersion);
        message.setAttemptNo(nextAttemptNo);
        message.setDeliverAt(deliverAt);
        message.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);

        DataMerchantNotificationRetryOutboxDO event = new DataMerchantNotificationRetryOutboxDO();
        event.setEventNo(eventNo);
        event.setAggregateType("MERCHANT_NOTIFICATION");
        event.setAggregateNo(task.getNotifyId());
        event.setTransactionId(task.getTransactionId());
        event.setOperationId(task.getOperationId());
        event.setMerchantId(task.getMerchantId());
        event.setMerchantOrderNo(task.getMerchantOrderNo());
        event.setTransactionType("MERCHANT_NOTIFICATION");
        event.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        event.setEventStatus("INIT");
        event.setTopic(MqTopic.PAYMENT_EVENT);
        event.setTag(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        event.setMessageKey(eventNo);
        event.setMessageGroup(task.getTransactionId());
        event.setPayloadJson(JsonUtils.toJsonString(message));
        event.setRetryCount(0);
        event.setMaxRetryCount(OUTBOX_MAX_RETRY_COUNT);
        event.setEventTime(now);
        event.setTransactionDateTime(task.getTransactionDateTime());
        event.setTransactionUtcTime(task.getTransactionDateTime()
                .atZone(ZoneId.of(PLATFORM_ZONE_ID))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        event.setTransactionTimeZone(PLATFORM_ZONE_ID);
        event.setVersion(0);
        event.setDeleted(0);
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    /** 使用任务号和失败状态版本生成稳定、定长 Outbox 事件号。 */
    private String eventNo(String notifyId, int expectedVersion) {
        String source = notifyId + ":" + expectedVersion;
        return "MNR-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}
