package com.scott.payment.risk.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.risk.domain.FrequencySuccessReservationTransitionSummary;
import com.scott.payment.risk.mq.message.RiskPaymentTransactionEventMessage;
import com.scott.payment.risk.service.FrequencySuccessReservationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 根据支付终态确认或释放频控成功名额的独立 MQ 消费者。
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
        consumerGroup = RiskMqConstants.FREQUENCY_SUCCESS_LIFECYCLE_CONSUMER_GROUP,
        selectorExpression = RiskMqConstants.PAYMENT_TRANSACTION_CREATED_TAG
                + " || " + RiskMqConstants.PAYMENT_TRANSACTION_CALLBACK_PROCESSED_TAG
                + " || " + RiskMqConstants.PAYMENT_TRANSACTION_STATUS_CHANGED_TAG,
        messageModel = MessageModel.CLUSTERING)
public class FrequencySuccessReservationPaymentEventConsumer
        implements RocketMQListener<String> {

    private static final String PAYMENT_SUCCESS = "SUCCESS";

    private static final String PAYMENT_FAILED = "FAILED";

    private final FrequencySuccessReservationService reservationService;

    public FrequencySuccessReservationPaymentEventConsumer(
            FrequencySuccessReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * 消费支付状态事件；处理中状态保留名额，终态事件按交易幂等推进。
     *
     * @param payload 支付交易事件 JSON，不得包含卡数据或凭据
     */
    @Override
    public void onMessage(String payload) {
        RiskPaymentTransactionEventMessage message =
                JsonUtils.parseObject(payload, RiskPaymentTransactionEventMessage.class);
        if (message == null
                || !StringUtils.hasText(message.getMerchantId())
                || !StringUtils.hasText(message.getTransactionId())
                || !StringUtils.hasText(message.getTransactionStatus())) {
            log.warn("event: RISK_FREQUENCY_SUCCESS_PAYMENT_EVENT_SKIPPED reason=messageInvalid payloadLength: {}",
                    payload == null ? 0 : payload.length());
            return;
        }
        String status = message.getTransactionStatus().trim().toUpperCase(Locale.ROOT);
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            FrequencySuccessReservationTransitionSummary summary;
            if (PAYMENT_SUCCESS.equals(status)) {
                summary = reservationService.confirm(message.getMerchantId(), message.getTransactionId());
            } else if (PAYMENT_FAILED.equals(status)) {
                summary = reservationService.release(message.getMerchantId(), message.getTransactionId());
            } else {
                return;
            }
            log.info("event: RISK_FREQUENCY_SUCCESS_PAYMENT_EVENT_APPLIED traceId: {} messageId: {} transactionId: {} paymentStatus: {} applied: {} idempotent: {} conflicted: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getTransactionId(),
                    status,
                    summary.applied(),
                    summary.idempotent(),
                    summary.conflicted());
        } finally {
            TraceContext.clear();
        }
    }
}
