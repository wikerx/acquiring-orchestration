package com.scott.payment.admin.mq;

import com.scott.payment.admin.api.internal.AdminPaymentCapabilityOperationsInternalController;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityEventPublisher.Message;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityOperations;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityOperations.RecoveryRequest;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminPaymentCapabilityConsumerTests {

    private final MerchantPaymentCapabilityOperations operations = mock(MerchantPaymentCapabilityOperations.class);
    private final AdminPaymentCapabilityConsumer consumer = new AdminPaymentCapabilityConsumer(operations);

    @Test
    void preservesSourceEventIdentityWhenDispatchingToOwner() {
        Message message = message();
        consumer.onMessage(JsonUtils.toJsonString(message));
        verify(operations).consume(argThat(event -> event.getMessageId().equals(message.getMessageId())
                && event.getApplicationItemId().equals(19L) && event.getRoundNo().equals(2)));
    }

    @Test
    void rejectsIncompatibleEventBeforeOwnerWrites() {
        Message message = message();
        message.setSchemaVersion(2);
        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .hasMessageContaining("invalid payment capability event");
        verifyNoInteractions(operations);
    }

    @Test
    void preventsOtherInternalCallersFromRunningPaymentCapabilityJobs() {
        var controller = new AdminPaymentCapabilityOperationsInternalController(operations);
        assertThatThrownBy(() -> controller.recover("service-payment", new RecoveryRequest(100)))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(operations);
    }

    private static Message message() {
        Message message = new Message();
        message.setMessageId("a".repeat(64));
        message.setSchemaVersion(1);
        message.setMerchantId("test-merchant");
        message.setItemKey("CARD_BRAND:VISA");
        message.setAction("APPROVE");
        message.setApplicationItemId(19L);
        message.setRoundNo(2);
        message.setOccurredAt(LocalDateTime.of(2026, 11, 20, 18, 0));
        return message;
    }
}
