package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.component.mq.observability.MqOutboxOperationalMetrics;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.model.TransactionEventOutboxMetricsSnapshot;
import com.scott.payment.payment.service.TransactionEventOutboxRelayService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionEventOutboxRelayService
 * @date : 2026-07-12 18:45
 * @email : scott_x@163.com
 * @description : 交易本地消息投递默认实现，位于 service-payment 服务实现层，通过本地消息表实现事务提交后 RocketMQ 最终一致投递。
 * @status : create
 */
@Slf4j
@Service
public class DefaultTransactionEventOutboxRelayService implements TransactionEventOutboxRelayService {

    /**
     * 默认失败重试间隔分钟数。
     */
    private static final long DEFAULT_RETRY_DELAY_MINUTES = 1L;

    /** 商户通知重试时间按平台交易时区转换为 RocketMQ 绝对时间戳。 */
    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 交易本地消息服务。
     */
    private final TransactionEventOutboxService eventOutboxService;

    /**
     * RocketMQ 生产者。
     */
    private final MqProducer mqProducer;

    /** PROCESSING 状态允许保留的秒数，超时后由下一轮扫描恢复。 */
    private final long processingTimeoutSeconds;
    /** 交易 Outbox 低基数运维指标。 */
    private final MqOutboxOperationalMetrics metrics;

    /**
     * 创建交易本地消息投递服务。
     *
     * @param eventOutboxService 交易本地消息服务
     * @param mqProducer         RocketMQ 生产者
     */
    @Autowired
    public DefaultTransactionEventOutboxRelayService(
            TransactionEventOutboxService eventOutboxService,
            MqProducer mqProducer,
            @Value("${payment.transaction.outbox.processing-timeout-seconds:120}") long processingTimeoutSeconds,
            MqOutboxOperationalMetrics metrics) {
        this.eventOutboxService = eventOutboxService;
        this.mqProducer = mqProducer;
        this.processingTimeoutSeconds = Math.max(processingTimeoutSeconds, 1L);
        this.metrics = metrics;
    }

    /** 测试和独立组件环境使用默认 PROCESSING 超时配置。 */
    DefaultTransactionEventOutboxRelayService(TransactionEventOutboxService eventOutboxService,
                                               MqProducer mqProducer) {
        this(eventOutboxService, mqProducer, 120L, MqOutboxOperationalMetrics.noop());
    }

    /**
     * 投递指定事件时间所在季度分表中的到期事件。
     *
     * @param eventTime 事件时间，用于 ShardingSphere 精确定位季度
     * @param limit     最大投递条数
     * @return 本次成功投递数量
     */
    @Override
    public int publishDueEvents(LocalDateTime eventTime, int limit) {
        long startNanos = System.nanoTime();
        String outcome = "failure";
        try {
            LocalDateTime now = LocalDateTime.now();
            int recovered = eventOutboxService.recoverStaleProcessing(
                    eventTime, now.minusSeconds(processingTimeoutSeconds), now);
            if (recovered > 0) {
                log.warn("event: TRANSACTION_OUTBOX_PROCESSING_RECOVERED recoveredCount: {} eventQuarter: {}",
                        recovered, eventTime);
            }
            int batchSize = Math.max(limit, 1);
            List<TransactionEventOutboxDO> events = eventOutboxService.listDueEvents(eventTime, now, batchSize);
            metrics.recordBatchSize(MqOutboxOperationalMetrics.TRANSACTION_OUTBOX, events.size(), batchSize);
            int successCount = 0;
            for (TransactionEventOutboxDO eventDO : events) {
                if (publishSingle(eventDO, now)) {
                    successCount++;
                }
            }
            outcome = "success";
            return successCount;
        } finally {
            metrics.recordRelayDuration(
                    MqOutboxOperationalMetrics.TRANSACTION_OUTBOX,
                    outcome,
                    System.nanoTime() - startNanos);
        }
    }

    /** 从所有已发布季度汇总交易 Outbox 状态并刷新 Gauge。 */
    @Override
    public void refreshMetrics(List<LocalDateTime> publishedQuarters) {
        long init = 0L;
        long processing = 0L;
        long failed = 0L;
        long closed = 0L;
        LocalDateTime oldest = null;
        if (publishedQuarters != null) {
            for (LocalDateTime quarter : publishedQuarters) {
                TransactionEventOutboxMetricsSnapshot snapshot = eventOutboxService.metricsSnapshot(quarter);
                if (snapshot == null) {
                    continue;
                }
                init += count(snapshot.getInitCount());
                processing += count(snapshot.getProcessingCount());
                failed += count(snapshot.getFailedCount());
                closed += count(snapshot.getClosedCount());
                if (snapshot.getOldestPendingTime() != null
                        && (oldest == null || snapshot.getOldestPendingTime().isBefore(oldest))) {
                    oldest = snapshot.getOldestPendingTime();
                }
            }
        }
        metrics.updateTransaction(init, processing, failed, closed, oldest, LocalDateTime.now());
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 至少一次投递单个交易 Outbox 事件。
     *
     * <p>MQ 发送成功后再 CAS 标记 SENT；若标记失败，事件可能被再次发送，因此消费者必须按消息号幂等。
     * 发送异常会记录脱敏失败原因和下次重试时间。</p>
     *
     * @param eventDO 待投递事件
     * @param now     本批次处理时间
     * @return true 表示消息已发送且 Outbox 成功标记为 SENT
     */
    private boolean publishSingle(TransactionEventOutboxDO eventDO, LocalDateTime now) {
        long startNanos = System.nanoTime();
        if (!eventOutboxService.claimForPublish(eventDO, now)) {
            log.info("event: TRANSACTION_OUTBOX_CLAIM_SKIPPED eventNo: {} transactionId: {} version: {}",
                    eventDO.getEventNo(), eventDO.getTransactionId(), eventDO.getVersion());
            return false;
        }
        try {
            BaseMqMessage message = buildMessage(eventDO);
            if (!StringUtils.hasText(message.getMessageId())) {
                message.setMessageId(eventDO.getMessageKey());
            }
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(eventDO.getEventTime());
            }
            if (!StringUtils.hasText(message.getTraceId())) {
                message.setTraceId(TraceContext.getOrCreateTraceId());
            }
            message.setRetryCount(Math.max(eventDO.getRetryCount() == null ? 0 : eventDO.getRetryCount(), 0));
            log.info("event: TRANSACTION_OUTBOX_PUBLISH_START stage=MQ traceId: {} eventNo: {} messageId: {} messageKey: {} retryCount: {} topic: {} tag: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} transactionType: {} transactionDateTime: {}",
                    message.getTraceId(),
                    eventDO.getEventNo(),
                    message.getMessageId(),
                    eventDO.getMessageKey(),
                    message.getRetryCount(),
                    eventDO.getTopic(),
                    eventDO.getTag(),
                    eventDO.getTransactionId(),
                    eventDO.getOperationId(),
                    eventDO.getMerchantId(),
                    eventDO.getMerchantOrderNo(),
                    eventDO.getTransactionType(),
                    eventDO.getTransactionDateTime());
            sendMessage(eventDO, message);
            boolean updated = eventOutboxService.markSent(eventDO, LocalDateTime.now());
            if (!updated) {
                log.warn("event: TRANSACTION_OUTBOX_MARK_SENT_CAS_FAILED stage=MQ traceId: {} eventNo: {} messageId: {} messageKey: {} transactionId: {} operationId: {} durationMs: {}",
                        message.getTraceId(),
                        eventDO.getEventNo(),
                        message.getMessageId(),
                        eventDO.getMessageKey(),
                        eventDO.getTransactionId(),
                        eventDO.getOperationId(),
                        elapsedMillis(startNanos));
            } else {
                log.info("event: TRANSACTION_OUTBOX_PUBLISH_END stage=MQ traceId: {} eventNo: {} messageId: {} messageKey: {} transactionId: {} operationId: {} status=SENT durationMs: {}",
                        message.getTraceId(),
                        eventDO.getEventNo(),
                        message.getMessageId(),
                        eventDO.getMessageKey(),
                        eventDO.getTransactionId(),
                        eventDO.getOperationId(),
                        elapsedMillis(startNanos));
            }
            return updated;
        } catch (Exception exception) {
            LocalDateTime nextRetryTime = now.plusMinutes(DEFAULT_RETRY_DELAY_MINUTES);
            String failureType = safeFailReason(exception);
            boolean failedStateRecorded = eventOutboxService.markFailed(
                    eventDO, nextRetryTime, failureType, now);
            if (!failedStateRecorded) {
                log.error("event: TRANSACTION_OUTBOX_MARK_FAILED_CAS_FAILED stage=MQ traceId: {} eventNo: {} messageKey: {} transactionId: {} operationId: {} expectedVersion: {} errorType: {}",
                        TraceContext.getTraceId(),
                        eventDO.getEventNo(),
                        eventDO.getMessageKey(),
                        eventDO.getTransactionId(),
                        eventDO.getOperationId(),
                        eventDO.getVersion(),
                        failureType);
            }
            log.warn("event: TRANSACTION_OUTBOX_PUBLISH_FAILED stage=MQ traceId: {} eventNo: {} messageKey: {} transactionId: {} operationId: {} retryCount: {} nextRetryTime: {} errorType: {} stateRecorded: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    eventDO.getEventNo(),
                    eventDO.getMessageKey(),
                    eventDO.getTransactionId(),
                    eventDO.getOperationId(),
                    eventDO.getRetryCount(),
                    nextRetryTime,
                    failureType,
                    failedStateRecorded,
                    elapsedMillis(startNanos));
            return false;
        }
    }

    /** 自动重试事件使用绝对定时投递，生命周期事件强制顺序投递，其它事件使用普通发送。 */
    private void sendMessage(TransactionEventOutboxDO eventDO, BaseMqMessage message) {
        if (message instanceof MerchantNotificationRetryDueMessage retryMessage) {
            if (retryMessage.getDeliverAt() == null) {
                throw new IllegalStateException("merchant notification retry deliver time is required");
            }
            mqProducer.sendAt(
                    eventDO.getTopic(),
                    eventDO.getTag(),
                    retryMessage,
                    retryMessage.getDeliverAt().atZone(PLATFORM_ZONE_ID).toInstant());
            return;
        }
        if (isLifecycleEvent(eventDO.getTag()) && !StringUtils.hasText(eventDO.getMessageGroup())) {
            throw new IllegalStateException("transaction lifecycle event message group is required");
        }
        if (StringUtils.hasText(eventDO.getMessageGroup())) {
            mqProducer.sendOrderly(eventDO.getTopic(), eventDO.getTag(), message, eventDO.getMessageGroup());
            return;
        }
        mqProducer.send(eventDO.getTopic(), eventDO.getTag(), message);
    }

    private boolean isLifecycleEvent(String tag) {
        return MqTag.TRANSACTION_CREATED.equals(tag)
                || MqTag.TRANSACTION_STATUS_CHANGED.equals(tag)
                || MqTag.TRANSACTION_CALLBACK_PROCESSED.equals(tag);
    }

    /**
     * 计算本地消息投递耗时。
     *
     * @param startNanos System.nanoTime 起始值
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 从本地事件表载荷恢复 MQ 消息体。
     * <p>
     * 前置条件：eventDO 来自 transaction_event_outbox 分表，payloadJson 可能是历史版本消息。
     * 该方法优先反序列化为明确消息类型；载荷为空、畸形或无法识别时抛出异常并进入 Outbox 重试，
     * 禁止发送缺失交易身份的空事件。
     * </p>
     * @param eventDO 本地事件表记录，提供 payloadJson、topic、tag 和业务标识
     * @return 可交给 MQ 生产者发送的基础消息
     */
    private BaseMqMessage buildMessage(TransactionEventOutboxDO eventDO) {
        if (MqTag.MERCHANT_NOTIFICATION_RETRY_DUE.equals(eventDO.getTag())) {
            MerchantNotificationRetryDueMessage retryMessage = JsonUtils.parseObject(
                    eventDO.getPayloadJson(), MerchantNotificationRetryDueMessage.class);
            if (retryMessage == null) {
                throw new IllegalStateException("merchant notification retry outbox payload is invalid");
            }
            return retryMessage;
        }
        if (MqTag.REFUND_EXECUTION_REQUESTED.equals(eventDO.getTag())) {
            RefundExecutionMessage executionMessage = JsonUtils.parseObject(
                    eventDO.getPayloadJson(), RefundExecutionMessage.class);
            if (executionMessage == null) {
                throw new IllegalStateException("refund execution outbox payload is invalid");
            }
            return executionMessage;
        }
        TransactionEventMessage message = JsonUtils.parseObject(eventDO.getPayloadJson(), TransactionEventMessage.class);
        if (message == null || !StringUtils.hasText(message.getEventType())) {
            throw new IllegalStateException("transaction outbox payload is invalid");
        }
        return message;
    }

    /** 返回不包含异常正文或消息载荷的失败类型摘要。 */
    private String safeFailReason(Exception exception) {
        String failureType = exception.getClass().getSimpleName();
        return StringUtils.hasText(failureType) ? failureType : "MqPublishException";
    }
}
