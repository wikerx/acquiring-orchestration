package com.scott.payment.clearing.domain.service;

import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingEventValidatorTest
 * @date : 2026-08-26 08:25
 * @email : scott_x@163.com
 * @description : 验证清分入口拒绝缺失业务身份、分片键和不受支持事件，消息状态不能替代数据库终态。
 * @status : create
 */
class ClearingEventValidatorTest {

    private final ClearingEventValidator validator = new ClearingEventValidator();

    @Test
    void shouldAcceptStatusChangedAndRetryDueEventsWithCompleteIdentity() {
        validator.validate(message(MqTag.TRANSACTION_STATUS_CHANGED));
        validator.validate(retryMessage());
    }

    @Test
    void shouldRejectRetryDueWithoutControlFields() {
        assertThatThrownBy(() -> validator.validate(message(MqTag.TRANSACTION_CLEARING_RETRY_DUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry");
    }

    @Test
    void shouldRejectMissingShardTime() {
        PaymentTransactionEventMessage message = message(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionDateTime(null);

        assertThatThrownBy(() -> validator.validate(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transactionDateTime");
    }

    @Test
    void shouldRejectClearingCompletedAsAnInputTrigger() {
        assertThatThrownBy(() -> validator.validate(message(MqTag.TRANSACTION_CLEARING_COMPLETED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    private PaymentTransactionEventMessage message(String eventType) {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG-CLEARING-001");
        message.setTransactionId("202608260001");
        message.setOperationId("OP-001");
        message.setMerchantId("M-001");
        message.setMerchantOrderNo("ORDER-001");
        message.setEventType(eventType);
        message.setTransactionStatus("SUCCESS");
        message.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 8, 20));
        return message;
    }

    private ClearingRetryDueMessage retryMessage() {
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        message.setMessageId("MSG-CLEARING-RETRY-001");
        message.setTransactionId("202608260001");
        message.setOperationId("OP-001");
        message.setMerchantId("M-001");
        message.setMerchantOrderNo("ORDER-001");
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionStatus("SUCCESS");
        message.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 8, 20));
        message.setSourceEventNo("MSG-CLEARING-001");
        message.setExpectedClearingRevision(0);
        message.setClearingRetryCount(1);
        message.setRetryReasonCode("SOURCE_CLEARING_PENDING");
        message.setDeliverAt(LocalDateTime.of(2026, 8, 26, 8, 21).toInstant(ZoneOffset.UTC));
        return message;
    }
}
