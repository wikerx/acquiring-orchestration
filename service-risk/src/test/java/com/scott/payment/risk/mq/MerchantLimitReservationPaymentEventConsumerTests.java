package com.scott.payment.risk.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.mq.message.RiskPaymentTransactionEventMessage;
import com.scott.payment.risk.service.MerchantLimitReservationLifecycleCoordinator;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付状态事件消费者测试，验证成功确认、失败取消和处理中状态保留预占。
 */
class MerchantLimitReservationPaymentEventConsumerTests {

    @Test
    void shouldApplySuccessAndFailureButRetainPendingReservation() {
        MerchantLimitReservationLifecycleCoordinator coordinator =
                mock(MerchantLimitReservationLifecycleCoordinator.class);
        when(coordinator.applyPaymentStatus(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(MerchantLimitReservationTransitionSummary.empty());
        MerchantLimitReservationPaymentEventConsumer consumer =
                new MerchantLimitReservationPaymentEventConsumer(coordinator);

        consumer.onMessage(payload("SUCCESS"));
        consumer.onMessage(payload("FAILED"));
        consumer.onMessage(payload("PENDING"));

        verify(coordinator).applyPaymentStatus(
                "TX1001", "SUCCESS", "payment event TRANSACTION_STATUS_CHANGED");
        verify(coordinator).applyPaymentStatus(
                "TX1001", "FAILED", "payment event TRANSACTION_STATUS_CHANGED");
        verify(coordinator).applyPaymentStatus(
                "TX1001", "PENDING", "payment event TRANSACTION_STATUS_CHANGED");
        verify(coordinator, never()).cancel(
                org.mockito.ArgumentMatchers.eq("TX1001"),
                org.mockito.ArgumentMatchers.contains("PENDING"));
    }

    private String payload(String status) {
        RiskPaymentTransactionEventMessage message =
                new RiskPaymentTransactionEventMessage();
        message.setMessageId("EV-" + status);
        message.setTransactionId("TX1001");
        message.setTransactionStatus(status);
        message.setEventType("TRANSACTION_STATUS_CHANGED");
        return JsonUtils.toJsonString(message);
    }
}
