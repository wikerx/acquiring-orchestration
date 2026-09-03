package com.scott.payment.clearing.domain.service;

import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingEventValidator
 * @date : 2026-08-26 08:28
 * @email : scott_x@163.com
 * @description : 校验清分消息的业务身份和分片键；消息状态只作审计提示，数据库动作事实仍是终态权威来源。
 * @status : create
 */
@Component
public class ClearingEventValidator {

    private static final Set<String> INPUT_EVENTS = Set.of(
            MqTag.TRANSACTION_STATUS_CHANGED,
            MqTag.TRANSACTION_CLEARING_RETRY_DUE);

    /**
     * 校验可进入清分应用层的消息契约。
     *
     * @param message 交易终态或清分延时重试消息
     * @throws IllegalArgumentException 缺少身份、分片时间或事件类型不受支持时抛出
     */
    public void validate(PaymentTransactionEventMessage message) {
        requireText(message == null ? null : message.getMessageId(), "messageId");
        requireText(message.getTransactionId(), "transactionId");
        requireText(message.getOperationId(), "operationId");
        requireText(message.getMerchantId(), "merchantId");
        requireText(message.getEventType(), "eventType");
        if (!INPUT_EVENTS.contains(message.getEventType())) {
            throw new IllegalArgumentException("eventType is not a clearing input event");
        }
        if (message.getTransactionDateTime() == null) {
            throw new IllegalArgumentException("transactionDateTime is required for sharding");
        }
        if (MqTag.TRANSACTION_CLEARING_RETRY_DUE.equals(message.getEventType())) {
            validateRetryControl(message);
        }
    }

    /** 延时重试消息必须携带原失败修订和重试序号，避免过期消息重新领取最新状态。 */
    private void validateRetryControl(PaymentTransactionEventMessage message) {
        if (!(message instanceof ClearingRetryDueMessage retryMessage)
                || !StringUtils.hasText(retryMessage.getSourceEventNo())
                || retryMessage.getExpectedClearingRevision() == null
                || retryMessage.getExpectedClearingRevision() < 0
                || retryMessage.getClearingRetryCount() == null
                || retryMessage.getClearingRetryCount() < 1
                || !StringUtils.hasText(retryMessage.getRetryReasonCode())
                || retryMessage.getDeliverAt() == null) {
            throw new IllegalArgumentException("clearing retry control fields are invalid");
        }
        try {
            ClearingFailureCodeEnum reason = ClearingFailureCodeEnum.valueOf(retryMessage.getRetryReasonCode());
            if (!reason.isRetryable()) {
                throw new IllegalArgumentException("clearing retry reason is not retryable");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("clearing retry reason code is invalid", exception);
        }
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
