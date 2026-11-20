package com.scott.payment.admin.mq;

import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityEventPublisher;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityEventPublisher.Message;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityOperations;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTopic;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 按原事件号执行支付方式待办及业务邮件。 */
@Slf4j
@Component
@RocketMQMessageListener(topic = MqTopic.PAYMENT_EVENT,
        consumerGroup = "acquiring_admin_payment_capability_v1",
        selectorExpression = MerchantPaymentCapabilityEventPublisher.TAG)
public class AdminPaymentCapabilityConsumer implements RocketMQListener<String> {

    private static final Set<String> ACTIONS = Set.of("SUBMIT", "RESUBMIT", "WITHDRAW", "APPROVE", "RETURN", "REJECT",
            "SUSPEND", "RESTORE", "TERMINATE", "VALIDITY", "READY", "CONFIGURATION_CHANGED", "FEE_CHANGED",
            "AUTH_EFFECTIVE", "AUTH_EXPIRED");
    private final MerchantPaymentCapabilityOperations operations;

    public AdminPaymentCapabilityConsumer(MerchantPaymentCapabilityOperations operations) {
        this.operations = operations;
    }

    @Override
    public void onMessage(String payload) {
        Message event = JsonUtils.parseObject(payload, Message.class);
        if (event == null || event.getSchemaVersion() != 1 || event.getMessageId() == null
                || !event.getMessageId().matches("[a-f0-9]{64}") || event.getMerchantId() == null
                || event.getMerchantId().isBlank() || event.getMerchantId().length() > 32
                || event.getOccurredAt() == null || event.getAction() == null || !ACTIONS.contains(event.getAction())
                || (event.getItemKey() != null && event.getItemKey().length() > 64)
                || (event.getApplicationItemId() != null && (event.getRoundNo() == null || event.getItemKey() == null))) {
            throw new IllegalArgumentException("invalid payment capability event");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(event.getTraceId()));
        try {
            operations.consume(event);
        } catch (RuntimeException exception) {
            log.warn("支付方式事件待恢复, event: ADMIN_PAYMENT_CAPABILITY_EVENT_PENDING, eventId: {}, errorType: {}",
                    event.getMessageId(), exception.getClass().getSimpleName());
            throw exception;
        } finally {
            TraceContext.clear();
        }
    }
}
