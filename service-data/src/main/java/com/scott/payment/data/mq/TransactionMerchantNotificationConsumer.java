package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryMessage;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
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
 * service-data 商户通知命令消费者，仅处理自动到期投递和人工重发消息。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "data.merchant-notification.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_EVENT,
        consumerGroup = DataMqConsumerGroups.MERCHANT_NOTIFICATION,
        selectorExpression = MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED
                + " || " + MqTag.MERCHANT_NOTIFICATION_RETRY_DUE,
        messageModel = MessageModel.CLUSTERING
)
public class TransactionMerchantNotificationConsumer implements RocketMQListener<String> {

    /**
     * 商户通知投递服务，数据库版本 CAS 提供最终重复消费保护。
     */
    private final MerchantNotificationDeliveryService deliveryService;

    /**
     * 创建商户通知命令消费者。
     *
     * @param deliveryService 商户通知投递服务
     */
    public TransactionMerchantNotificationConsumer(MerchantNotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * 消费到期或人工重发命令并触发商户通知。
     *
     * <p>消息本身不作为通知状态事实；重复消息由通知任务状态和版本 CAS 吸收。无效消息抛出后由
     * RocketMQ 重试和死信处理，避免静默确认造成通知永久丢失。</p>
     *
     * @param payload RocketMQ JSON 消息体，不允许包含卡数据或密钥
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        String eventType = resolveEventType(payload);
        if (MqTag.MERCHANT_NOTIFICATION_RETRY_DUE.equals(eventType)) {
            consumeAutomaticRetry(payload, startNanos);
            return;
        }
        if (MqTag.MERCHANT_NOTIFICATION_RETRY_REQUESTED.equals(eventType)) {
            consumeManualRetry(payload, startNanos);
            return;
        }
        log.debug("非商户通知命令已忽略, event: DATA_MERCHANT_NOTIFY_EVENT_IGNORED, traceId: {}, reason: notDeliveryCommand, payloadLength: {}, durationMs: {}",
                TraceContext.getTraceId(), payload == null ? 0 : payload.length(), elapsedMillis(startNanos));
    }

    /**
     * 解析通知命令类型；空载荷、畸形 JSON 或缺少事件类型均交由 Broker 重试。
     *
     * @param payload RocketMQ JSON 消息体
     * @return 通知命令事件类型
     */
    private String resolveEventType(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("merchant notification payload is empty");
        }
        try {
            MerchantNotificationRetryDueMessage message = JsonUtils.parseObject(
                    payload, MerchantNotificationRetryDueMessage.class);
            if (message == null || !StringUtils.hasText(message.getEventType())) {
                throw new IllegalArgumentException("merchant notification event type is missing");
            }
            return message.getEventType();
        } catch (RuntimeException exception) {
            log.error("商户通知命令解析失败, event: DATA_MERCHANT_NOTIFY_COMMAND_DESERIALIZE_FAILED, payloadLength: {}, exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("merchant notification payload is invalid", exception);
        }
    }

    /**
     * 消费自动重试事件；未来消息抛出异常交由 RocketMQ 重新投递。
     *
     * @param payload RocketMQ JSON 消息体
     * @param startNanos 消费开始单调时钟
     */
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
            log.error("自动重试到期命令缺少必填字段, event: DATA_MERCHANT_NOTIFY_RETRY_DUE_INVALID, traceId: {}, reason: requiredFieldMissing, payloadLength: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length());
            throw new IllegalArgumentException("merchant notification retry due fields are missing");
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
            log.info("自动重试到期命令消费完成, event: DATA_MERCHANT_NOTIFY_RETRY_DUE_CONSUMED, traceId: {}, messageId: {}, notifyId: {}, transactionId: {}, expectedVersion: {}, attemptNo: {}, notified: {}, durationMs: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getNotifyId(),
                    message.getTransactionId(), message.getExpectedVersion(), message.getAttemptNo(),
                    notified, elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 消费管理端或商户端人工重发事件，使用消息号固定本次回调 eventId。
     *
     * <p>商户端消息必须同时提供任务号和商户号，并在通知数据源中再次精确校验归属；管理端历史消息
     * 两个兼容字段均为空时继续按交易号定位。只传其中一个字段视为无效命令并交由死信治理。</p>
     *
     * @param payload RocketMQ JSON 消息体
     * @param startNanos 消费开始单调时钟
     */
    private void consumeManualRetry(String payload, long startNanos) {
        MerchantNotificationRetryMessage message;
        try {
            message = JsonUtils.parseObject(payload, MerchantNotificationRetryMessage.class);
        } catch (RuntimeException exception) {
            log.error("人工重发命令解析失败, event: DATA_MERCHANT_NOTIFY_RETRY_PARSE_FAILED, traceId: {}, payloadLength: {}, exceptionType: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length(),
                    exception.getClass().getSimpleName());
            throw new IllegalArgumentException("merchant notification retry payload is invalid", exception);
        }
        if (message == null
                || !StringUtils.hasText(message.getMessageId())
                || !StringUtils.hasText(message.getTransactionId())
                || message.getTransactionDateTime() == null) {
            log.error("人工重发命令缺少必填字段, event: DATA_MERCHANT_NOTIFY_RETRY_INVALID, traceId: {}, reason: requiredFieldMissing, payloadLength: {}, durationMs: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length(), elapsedMillis(startNanos));
            throw new IllegalArgumentException("merchant notification retry fields are missing");
        }
        boolean hasNotifyId = StringUtils.hasText(message.getNotifyId());
        boolean hasMerchantId = StringUtils.hasText(message.getMerchantId());
        if (hasNotifyId != hasMerchantId) {
            log.error("人工重发命令任务归属字段不完整, event: DATA_MERCHANT_NOTIFY_RETRY_SCOPE_INVALID, traceId: {}, messageId: {}, hasNotifyId: {}, hasMerchantId: {}",
                    TraceContext.getTraceId(), message.getMessageId(), hasNotifyId, hasMerchantId);
            throw new IllegalArgumentException(
                    "merchant notification retry scope fields must be provided together");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            boolean notified = hasNotifyId
                    ? deliveryService.retryNotification(
                            message.getTransactionDateTime(),
                            message.getTransactionId(),
                            message.getNotifyId(),
                            message.getMerchantId(),
                            message.getMessageId())
                    : deliveryService.retryTransaction(
                            message.getTransactionDateTime(),
                            message.getTransactionId(),
                            message.getMessageId());
            log.info("人工重发命令消费完成, event: DATA_MERCHANT_NOTIFY_RETRY_CONSUMED, traceId: {}, messageId: {}, requestId: {}, notifyId: {}, transactionId: {}, merchantId: {}, requestedBy: {}, notified: {}, durationMs: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getRequestId(),
                    message.getNotifyId(), message.getTransactionId(), message.getMerchantId(),
                    message.getRequestedBy(), notified, elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
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
