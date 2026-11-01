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
 * 商户通知补偿应用层输入和批次边界测试。
 */
class MerchantNotificationApplicationServiceTests {

    /**
     * 验证应用层到期补偿将单季度批量限制收敛到五条。
     */
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

    /**
     * 验证单笔通知完整传递交易分片时间和平台交易号。
     */
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

    /**
     * 验证缺少交易分片时间时在访问数据库前拒绝请求。
     */
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

    /**
     * 验证对账入口只补发 MQ 事件并限制每季度批量。
     */
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
