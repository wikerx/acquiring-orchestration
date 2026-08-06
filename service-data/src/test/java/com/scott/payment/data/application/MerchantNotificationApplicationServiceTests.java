package com.scott.payment.data.application;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyCommandDTO;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyDueCommandDTO;
import com.scott.payment.data.service.MerchantNotificationDeliveryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 商户通知补偿应用层输入和批次边界测试。 */
class MerchantNotificationApplicationServiceTests {

    @Test
    void notifyDueShouldCapSynchronousBatchAtFive() {
        MerchantNotificationDeliveryService deliveryService = mock(MerchantNotificationDeliveryService.class);
        MerchantNotificationApplicationService service = new MerchantNotificationApplicationService(deliveryService);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 8, 3, 3, 17, 58);
        MerchantNotificationNotifyDueCommandDTO command = new MerchantNotificationNotifyDueCommandDTO();
        command.setTransactionDateTime(transactionDateTime);
        command.setLimit(100);
        when(deliveryService.notifyDue(transactionDateTime, 5)).thenReturn(2);

        assertThat(service.notifyDue(command)).isEqualTo(2);

        verify(deliveryService).notifyDue(transactionDateTime, 5);
    }

    @Test
    void notifyTransactionShouldForwardExplicitShardTimeAndTransactionId() {
        MerchantNotificationDeliveryService deliveryService = mock(MerchantNotificationDeliveryService.class);
        MerchantNotificationApplicationService service = new MerchantNotificationApplicationService(deliveryService);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 8, 3, 3, 17, 58);
        MerchantNotificationNotifyCommandDTO command = new MerchantNotificationNotifyCommandDTO();
        command.setTransactionId("202608030317582640931");
        command.setTransactionDateTime(transactionDateTime);
        when(deliveryService.notifyTransaction(transactionDateTime, command.getTransactionId())).thenReturn(true);

        assertThat(service.notifyTransaction(command)).isTrue();

        verify(deliveryService).notifyTransaction(transactionDateTime, command.getTransactionId());
    }

    @Test
    void notifyTransactionShouldRejectMissingShardTime() {
        MerchantNotificationApplicationService service =
                new MerchantNotificationApplicationService(mock(MerchantNotificationDeliveryService.class));
        MerchantNotificationNotifyCommandDTO command = new MerchantNotificationNotifyCommandDTO();
        command.setTransactionId("202608030317582640931");

        assertThatThrownBy(() -> service.notifyTransaction(command))
                .isInstanceOf(ServiceException.class)
                .hasMessage("transaction_date_time is required");
    }
}
