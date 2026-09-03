package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.payment.domain.refund.RefundExecutionOutcomeEnum;
import com.scott.payment.payment.service.RefundExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundExecutionConsumer
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 退款审批执行 MQ 消费者，仅解析非敏感执行身份并委托数据库状态机处理至少一次投递。
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "payment.refund.management", name = "execution-mq-enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_TRANSACTION_FIFO,
        consumerGroup = "service-payment-refund-execution",
        selectorExpression = MqTag.REFUND_EXECUTION_REQUESTED,
        consumeMode = ConsumeMode.ORDERLY,
        messageModel = MessageModel.CLUSTERING
)
public class RefundExecutionConsumer implements RocketMQListener<String> {

    private final RefundExecutionService refundExecutionService;

    /** @param refundExecutionService 退款执行消息状态机 */
    public RefundExecutionConsumer(RefundExecutionService refundExecutionService) {
        this.refundExecutionService = refundExecutionService;
    }

    /**
     * 消费退款执行消息；无法定位业务身份的毒消息抛出后交由 RocketMQ 重试和死信处理。
     *
     * @param payload 不含卡数据和渠道凭据的 JSON 消息
     */
    @Override
    public void onMessage(String payload) {
        RefundExecutionMessage message = parse(payload);
        if (!isValid(message)) {
            log.error("event: REFUND_EXECUTION_MESSAGE_INVALID traceId: {} reason=requiredFieldMissing payloadLength: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length());
            throw new IllegalArgumentException("refund execution message required fields are missing");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            RefundExecutionOutcomeEnum outcome = refundExecutionService.execute(message);
            log.info("event: REFUND_EXECUTION_MESSAGE_CONSUMED traceId: {} messageId: {} approvalId: {} transactionId: {} retryCount: {} outcome: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getApprovalId(),
                    message.getRefundTransactionId(), message.getRetryCount(), outcome);
        } finally {
            TraceContext.clear();
        }
    }

    private RefundExecutionMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("refund execution payload is empty");
        }
        try {
            return JsonUtils.parseObject(payload, RefundExecutionMessage.class);
        } catch (RuntimeException exception) {
            log.error("event: REFUND_EXECUTION_MESSAGE_DESERIALIZE_FAILED payloadLength: {} exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("refund execution payload is invalid", exception);
        }
    }

    /** 校验退款执行状态机所需的完整定位和版本字段。 */
    private boolean isValid(RefundExecutionMessage message) {
        return message != null
                && StringUtils.hasText(message.getMessageId())
                && StringUtils.hasText(message.getApprovalId())
                && StringUtils.hasText(message.getRefundTransactionId())
                && message.getRefundTransactionDateTime() != null
                && message.getExpectedOperationVersion() != null
                && MqTag.REFUND_EXECUTION_REQUESTED.equals(message.getEventType());
    }
}
