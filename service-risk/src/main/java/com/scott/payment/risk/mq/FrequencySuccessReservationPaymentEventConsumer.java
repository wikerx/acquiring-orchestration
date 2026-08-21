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
 * @author : scott
 * @version : v1.0.0
 * @classname : FrequencySuccessReservationPaymentEventConsumer
 * @date : 2026-08-20 23:45
 * @email : scott_x@163.com
 * @description : 消费支付终态并幂等确认或释放频控成功名额，畸形消息交由 MQ 重试和死信处理
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
        consumerGroup = RiskMqConstants.FREQUENCY_SUCCESS_LIFECYCLE_CONSUMER_GROUP,
        selectorExpression = RiskMqConstants.PAYMENT_TRANSACTION_CREATED_TAG
                + " || " + RiskMqConstants.PAYMENT_TRANSACTION_CALLBACK_PROCESSED_TAG
                + " || " + RiskMqConstants.PAYMENT_TRANSACTION_STATUS_CHANGED_TAG,
        messageModel = MessageModel.CLUSTERING)
public class FrequencySuccessReservationPaymentEventConsumer
        implements RocketMQListener<String> {

    /** 支付成功终态。 */
    private static final String PAYMENT_SUCCESS = "SUCCESS";

    /** 支付失败终态。 */
    private static final String PAYMENT_FAILED = "FAILED";

    /** 频控成功名额预占服务。 */
    private final FrequencySuccessReservationService reservationService;

    /**
     * 创建频控成功名额事件消费者。
     *
     * @param reservationService 频控成功名额预占服务
     */
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
        RiskPaymentTransactionEventMessage message = parseMessage(payload);
        if (message == null
                || !StringUtils.hasText(message.getMerchantId())
                || !StringUtils.hasText(message.getTransactionId())
                || !StringUtils.hasText(message.getTransactionStatus())) {
            log.error("event: RISK_FREQUENCY_SUCCESS_PAYMENT_EVENT_INVALID reason=requiredFieldMissing payloadLength: {}",
                    payload == null ? 0 : payload.length());
            throw new IllegalArgumentException("risk payment event required fields are missing");
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

    /** 解析支付事件；失败时不记录原始消息，交由 RocketMQ 重试和死信处理。 */
    private RiskPaymentTransactionEventMessage parseMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("risk payment event payload is invalid");
        }
        try {
            return JsonUtils.parseObject(payload, RiskPaymentTransactionEventMessage.class);
        } catch (RuntimeException exception) {
            log.error("event: RISK_FREQUENCY_SUCCESS_PAYMENT_EVENT_DESERIALIZE_FAILED payloadLength: {} exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("risk payment event payload is invalid", exception);
        }
    }
}
