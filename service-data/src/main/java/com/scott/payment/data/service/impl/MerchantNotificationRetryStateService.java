package com.scott.payment.data.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.data.entity.DataMerchantNotificationRetryOutboxDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
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
 * @classname : MerchantNotificationRetryStateService
 * @date : 2026-08-06 12:36
 * @email : scott_x@163.com
 * @description : 在同一交易分片事务中推进商户通知失败状态并写入自动重试 Outbox
 * @status : create
 */
@Service
@Slf4j
public class MerchantNotificationRetryStateService {

    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String PLATFORM_ZONE_ID = "Asia/Shanghai";
    private static final int OUTBOX_MAX_RETRY_COUNT = 8;

    /** 通知任务状态 Mapper。 */
    private final DataMerchantNotificationMapper notificationMapper;
    /** 同分片交易 Outbox Mapper。 */
    private final DataMerchantNotificationRetryOutboxMapper outboxMapper;

    /** 创建失败状态事务服务。 */
    public MerchantNotificationRetryStateService(DataMerchantNotificationMapper notificationMapper,
                                                  DataMerchantNotificationRetryOutboxMapper outboxMapper) {
        this.notificationMapper = notificationMapper;
        this.outboxMapper = outboxMapper;
    }

    /**
     * 推进一次失败结果；仍可重试时在同一事务写入延迟 MQ 事件。
     *
     * @param task 当前通知任务快照
     * @param processingVersion PROCESSING 状态版本
     * @param nextStatus FAILED 或 CLOSED
     * @param nextRetryTime 下次回调时间，CLOSED 时为空
     * @param failReason 脱敏失败摘要
     * @param finishedTime 当前尝试结束时间
     * @param currentAttemptNo 当前已完成的 attempt
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(DataMerchantNotificationTaskDO task,
                              int processingVersion,
                              String nextStatus,
                              LocalDateTime nextRetryTime,
                              String failReason,
                              LocalDateTime finishedTime,
                              int currentAttemptNo) {
        if (!STATUS_FAILED.equals(nextStatus) && !STATUS_CLOSED.equals(nextStatus)) {
            throw new IllegalArgumentException("merchant notification failure status is invalid");
        }
        int affectedRows = notificationMapper.markFailed(
                task.getId(), task.getTransactionDateTime(), processingVersion, nextStatus,
                nextRetryTime, failReason, finishedTime);
        if (affectedRows != 1) {
            log.error("event: DATA_MERCHANT_NOTIFY_FAILURE_STATE_CONFLICT traceId: {} notifyId: {} transactionId: {} expectedVersion: {} targetStatus: {} affectedRows: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(),
                    processingVersion, nextStatus, affectedRows);
            throw new IllegalStateException("merchant notification state transition conflict");
        }
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
