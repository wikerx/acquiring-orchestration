package com.scott.payment.risk.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.mq.message.RiskPaymentTransactionEventMessage;
import com.scott.payment.risk.service.MerchantLimitReservationLifecycleCoordinator;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationPaymentEventConsumer
 * @date : 2026-08-20 23:45
 * @email : scott_x@163.com
 * @description : 消费支付状态事件并以数据库状态机确认或释放商户累计限额预占，畸形消息交由 MQ 重试和死信处理
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "risk.evaluation",
        name = "reservation-event-consumer-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_EVENT,
        consumerGroup = RiskMqConstants.MERCHANT_LIMIT_LIFECYCLE_CONSUMER_GROUP,
        selectorExpression = RiskMqConstants.PAYMENT_TRANSACTION_CREATED_TAG
                + " || " + RiskMqConstants.PAYMENT_TRANSACTION_CALLBACK_PROCESSED_TAG
                + " || " + RiskMqConstants.PAYMENT_TRANSACTION_STATUS_CHANGED_TAG,
        messageModel = MessageModel.CLUSTERING)
public class MerchantLimitReservationPaymentEventConsumer
        implements RocketMQListener<String> {

    /** 商户累计限额预占生命周期编排服务。 */
    private final MerchantLimitReservationLifecycleCoordinator coordinator;

    /**
     * 创建商户累计限额预占事件消费者。
     *
     * @param coordinator 预占生命周期编排服务
     */
    public MerchantLimitReservationPaymentEventConsumer(
            MerchantLimitReservationLifecycleCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void onMessage(String payload) {
        RiskPaymentTransactionEventMessage message = parseMessage(payload);
        if (message == null
                || !StringUtils.hasText(message.getTransactionId())
                || !StringUtils.hasText(message.getTransactionStatus())) {
            log.error("event: RISK_MERCHANT_LIMIT_PAYMENT_EVENT_INVALID reason=requiredFieldMissing payloadLength: {}",
                    payload == null ? 0 : payload.length());
            throw new IllegalArgumentException("risk payment event required fields are missing");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            MerchantLimitReservationTransitionSummary summary =
                    coordinator.applyPaymentStatus(
                            message.getTransactionId(),
                            message.getTransactionStatus(),
                            "payment event " + message.getEventType());
            log.info("event: RISK_MERCHANT_LIMIT_PAYMENT_EVENT_APPLIED traceId: {} messageId: {} transactionId: {} paymentStatus: {} eventType: {} applied: {} idempotent: {} conflicted: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getTransactionId(),
                    message.getTransactionStatus(),
                    message.getEventType(),
                    summary.applied(),
                    summary.idempotent(),
                    summary.conflicted());
        } finally {
            TraceContext.clear();
        }
    }

    /** 解析支付事件；失败时不记录原始消息，交由 RocketMQ 重试和死信处理。 */
    private RiskPaymentTransactionEventMessage parseMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("risk payment event payload is invalid");
        }
        try {
            return JsonUtils.parseObject(payload, RiskPaymentTransactionEventMessage.class);
        } catch (RuntimeException exception) {
            log.error("event: RISK_MERCHANT_LIMIT_PAYMENT_EVENT_DESERIALIZE_FAILED payloadLength: {} exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("risk payment event payload is invalid", exception);
        }
    }
}
