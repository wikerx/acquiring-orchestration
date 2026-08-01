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
 * 以支付终态事件确认或撤销商户累计限额预占。
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

    private final MerchantLimitReservationLifecycleCoordinator coordinator;

    public MerchantLimitReservationPaymentEventConsumer(
            MerchantLimitReservationLifecycleCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void onMessage(String payload) {
        RiskPaymentTransactionEventMessage message =
                JsonUtils.parseObject(payload, RiskPaymentTransactionEventMessage.class);
        if (message == null
                || !StringUtils.hasText(message.getTransactionId())
                || !StringUtils.hasText(message.getTransactionStatus())) {
            log.warn("event: RISK_MERCHANT_LIMIT_PAYMENT_EVENT_SKIPPED reason=messageInvalid payloadLength: {}",
                    payload == null ? 0 : payload.length());
            return;
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
}
