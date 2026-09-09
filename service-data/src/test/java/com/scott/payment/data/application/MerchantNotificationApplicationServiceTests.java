package com.scott.payment.data.application;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyCommandDTO;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyDueCommandDTO;
import com.scott.payment.data.api.internal.dto.MerchantNotificationReconcileCommandDTO;
import com.scott.payment.data.service.impl.MerchantNotificationRetryReconciliationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationApplicationServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户通知补偿应用层输入和批次边界测试。
 * @status : create
 */
class MerchantNotificationApplicationServiceTests {

    @Test
    void notifyDueShouldCapMqReconciliationBatchAtFive() {
        MerchantNotificationRetryReconciliationService reconciliationService =
                mock(MerchantNotificationRetryReconciliationService.class);
        MerchantNotificationApplicationService service = new MerchantNotificationApplicationService(
                reconciliationService);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 8, 3, 3, 17, 58);
        MerchantNotificationNotifyDueCommandDTO command = new MerchantNotificationNotifyDueCommandDTO();
        command.setTransactionDateTime(transactionDateTime);
        command.setLimit(100);
        when(reconciliationService.reconcile(5, List.of(transactionDateTime))).thenReturn(2);

        assertThat(service.notifyDue(command)).isEqualTo(2);

        verify(reconciliationService).reconcile(5, List.of(transactionDateTime));
    }

    @Test
    void notifyTransactionShouldForwardExplicitShardTimeAndTransactionId() {
        MerchantNotificationRetryReconciliationService reconciliationService =
                mock(MerchantNotificationRetryReconciliationService.class);
        MerchantNotificationApplicationService service = new MerchantNotificationApplicationService(
                reconciliationService);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 8, 3, 3, 17, 58);
        MerchantNotificationNotifyCommandDTO command = new MerchantNotificationNotifyCommandDTO();
        command.setTransactionId("202608030317582640931");
        command.setTransactionDateTime(transactionDateTime);
        when(reconciliationService.reconcileTransaction(
                command.getTransactionId(), transactionDateTime)).thenReturn(true);

        assertThat(service.notifyTransaction(command)).isTrue();

        verify(reconciliationService).reconcileTransaction(command.getTransactionId(), transactionDateTime);
    }

    @Test
    void notifyTransactionShouldRejectMissingShardTime() {
        MerchantNotificationApplicationService service =
                new MerchantNotificationApplicationService(
                        mock(MerchantNotificationRetryReconciliationService.class));
        MerchantNotificationNotifyCommandDTO command = new MerchantNotificationNotifyCommandDTO();
        command.setTransactionId("202608030317582640931");

        assertThatThrownBy(() -> service.notifyTransaction(command))
                .isInstanceOf(ServiceException.class)
                .hasMessage("transaction_date_time is required");
    }

    @Test
    void reconcileDueShouldUseMqReconciliationAndCapEachQuarterAtFive() {
        MerchantNotificationRetryReconciliationService reconciliationService =
                mock(MerchantNotificationRetryReconciliationService.class);
        MerchantNotificationApplicationService service =
                new MerchantNotificationApplicationService(reconciliationService);
        MerchantNotificationReconcileCommandDTO command = new MerchantNotificationReconcileCommandDTO();
        LocalDateTime quarter = LocalDateTime.of(2026, 4, 1, 0, 0);
        command.setTransactionDateTimes(List.of(quarter));
        command.setLimit(100);
        when(reconciliationService.reconcile(5, List.of(quarter))).thenReturn(2);

        assertThat(service.reconcileDue(command)).isEqualTo(2);

        verify(reconciliationService).reconcile(5, List.of(quarter));
    }
}
