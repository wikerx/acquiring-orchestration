package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.enums.PaymentTransactionEventStatus;
import com.scott.payment.component.mq.message.MerchantNotificationRetryMessage;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.data.service.MerchantNotificationDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationConsumer
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 交易终态事件消费者，只按消息携带的真实交易时间和交易号精确触发对应商户通知
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "data.merchant-notification.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_EVENT,
        consumerGroup = DataMqConsumerGroups.MERCHANT_NOTIFICATION,
        selectorExpression = MqTag.TRANSACTION_CALLBACK_PROCESSED
                + " || " + MqTag.TRANSACTION_STATUS_CHANGED
                + " || " + MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED
                + " || " + MqTag.MERCHANT_NOTIFICATION_RETRY_DUE,
        messageModel = MessageModel.CLUSTERING
)
public class TransactionMerchantNotificationConsumer implements RocketMQListener<String> {

    /** 商户通知投递服务，数据库版本 CAS 提供最终重复消费保护。 */
    private final MerchantNotificationDeliveryService deliveryService;

    /**
     * 创建交易终态商户通知消费者。
     *
     * @param deliveryService 商户通知投递服务
     */
    public TransactionMerchantNotificationConsumer(MerchantNotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * 消费支付事件并触发商户通知。
     *
     * <p>消息本身不作为通知状态事实；重复消息由通知任务状态和版本 CAS 吸收。无效消息不访问数据库，
     * 避免无法定位分表的毒消息无限重试。</p>
     *
     * @param payload RocketMQ JSON 消息体，不允许包含卡数据或密钥
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        if (isAutomaticRetryPayload(payload)) {
            consumeAutomaticRetry(payload, startNanos);
            return;
        }
        if (isManualRetryPayload(payload)) {
            consumeManualRetry(payload, startNanos);
            return;
        }
        PaymentTransactionEventMessage message = parseMessage(payload);
        if (message == null
                || message.getTransactionDateTime() == null
                || !StringUtils.hasText(message.getTransactionId())
                || !isTerminalEvent(message.getEventType())
                || !PaymentTransactionEventStatus.isTerminal(message.getTransactionStatus())) {
            log.warn("event: DATA_PAYMENT_EVENT_SKIPPED traceId: {} reason=messageInvalid payloadLength: {} durationMs: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length(), elapsedMillis(startNanos));
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            boolean notified = deliveryService.notifyTransaction(
                    message.getTransactionDateTime(), message.getTransactionId());
            int successCount = notified ? 1 : 0;
            log.info("event: DATA_PAYMENT_EVENT_CONSUMED traceId: {} messageId: {} retryCount: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} transactionType: {} eventType: {} notifyId: {} successCount: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getTransactionId(),
                    message.getOperationId(),
                    message.getMerchantId(),
                    message.getMerchantOrderNo(),
                    message.getTransactionType(),
                    message.getEventType(),
                    message.getNotifyId(),
                    successCount,
                    elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /** 判断消息是否为自动重试到期事件。 */
    private boolean isAutomaticRetryPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        try {
            MerchantNotificationRetryDueMessage message = JsonUtils.parseObject(
                    payload, MerchantNotificationRetryDueMessage.class);
            return message != null
                    && MqTag.MERCHANT_NOTIFICATION_RETRY_DUE.equals(message.getEventType());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 消费自动重试事件；未来消息抛出异常交由 RocketMQ 重新投递。 */
    private void consumeAutomaticRetry(String payload, long startNanos) {
        MerchantNotificationRetryDueMessage message = JsonUtils.parseObject(
                payload, MerchantNotificationRetryDueMessage.class);
        if (message == null
                || !StringUtils.hasText(message.getMessageId())
                || !StringUtils.hasText(message.getNotifyId())
                || !StringUtils.hasText(message.getTransactionId())
                || message.getTransactionDateTime() == null
                || message.getExpectedVersion() == null
                || message.getExpectedVersion() < 0
                || message.getAttemptNo() == null
                || message.getAttemptNo() <= 0
                || message.getDeliverAt() == null) {
            log.warn("event: DATA_MERCHANT_NOTIFY_RETRY_DUE_SKIPPED traceId: {} reason=messageInvalid payloadLength: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length());
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        if (message.getDeliverAt().isAfter(now)) {
            throw new IllegalStateException("merchant notification retry message arrived before deliver time");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            boolean notified = deliveryService.retryDue(
                    message.getTransactionDateTime(),
                    message.getTransactionId(),
                    message.getNotifyId(),
                    message.getExpectedVersion(),
                    message.getAttemptNo());
            log.info("event: DATA_MERCHANT_NOTIFY_RETRY_DUE_CONSUMED traceId: {} messageId: {} notifyId: {} transactionId: {} expectedVersion: {} attemptNo: {} notified: {} durationMs: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getNotifyId(),
                    message.getTransactionId(), message.getExpectedVersion(), message.getAttemptNo(),
                    notified, elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 识别后台人工重发事件；只读取非敏感 eventType 字段，不记录消息原文。
     *
     * @param payload RocketMQ JSON 消息体
     * @return true 表示人工重发事件
     */
    private boolean isManualRetryPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        try {
            MerchantNotificationRetryMessage message = JsonUtils.parseObject(
                    payload, MerchantNotificationRetryMessage.class);
            return message != null
                    && MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED.equals(message.getEventType());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 消费管理后台人工重发事件，使用消息号固定本次回调 eventId。
     *
     * @param payload RocketMQ JSON 消息体
     * @param startNanos 消费开始单调时钟
     */
    private void consumeManualRetry(String payload, long startNanos) {
        MerchantNotificationRetryMessage message;
        try {
            message = JsonUtils.parseObject(payload, MerchantNotificationRetryMessage.class);
        } catch (RuntimeException exception) {
            log.warn("event: DATA_MERCHANT_NOTIFY_RETRY_PARSE_FAILED traceId: {} payloadLength: {} exceptionType: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length(),
                    exception.getClass().getSimpleName());
            return;
        }
        if (message == null
                || !StringUtils.hasText(message.getMessageId())
                || !StringUtils.hasText(message.getTransactionId())
                || message.getTransactionDateTime() == null) {
            log.warn("event: DATA_MERCHANT_NOTIFY_RETRY_SKIPPED traceId: {} reason=messageInvalid payloadLength: {} durationMs: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length(), elapsedMillis(startNanos));
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            boolean notified = deliveryService.retryTransaction(
                    message.getTransactionDateTime(), message.getTransactionId(), message.getMessageId());
            log.info("event: DATA_MERCHANT_NOTIFY_RETRY_CONSUMED traceId: {} messageId: {} requestId: {} transactionId: {} requestedBy: {} notified: {} durationMs: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getRequestId(),
                    message.getTransactionId(), message.getRequestedBy(), notified, elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 只接受已经在 Payment 终态事务内写入的事件，创建事件不能驱动商户通知。
     *
     * @param eventType MQ Tag 对应的事件类型
     * @return true 表示通知任务在事件提交前已经完成激活
     */
    private boolean isTerminalEvent(String eventType) {
        return MqTag.TRANSACTION_CALLBACK_PROCESSED.equals(eventType)
                || MqTag.TRANSACTION_STATUS_CHANGED.equals(eventType);
    }

    /**
     * 安全解析交易事件；畸形 JSON 只记录载荷长度和异常类型，不输出消息原文。
     *
     * @param payload RocketMQ JSON 消息体
     * @return 可解析消息，格式非法时返回空
     */
    private PaymentTransactionEventMessage parseMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(payload, PaymentTransactionEventMessage.class);
        } catch (RuntimeException exception) {
            log.warn("event: DATA_PAYMENT_EVENT_PARSE_FAILED traceId: {} payloadLength: {} exceptionType: {}",
                    TraceContext.getTraceId(), payload.length(), exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 计算单条 MQ 消息处理耗时。
     *
     * @param startNanos 单调时钟起始值
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
