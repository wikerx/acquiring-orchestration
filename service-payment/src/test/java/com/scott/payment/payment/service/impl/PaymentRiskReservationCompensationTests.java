package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRiskReservationCompensationTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控累计限额预占补偿测试，验证失败交易触发取消且缺少预占标识时不发送无效请求。
 * @status : create
 */
class PaymentRiskReservationCompensationTests {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldCancelReservationOnlyAfterPaymentTransactionRollback() {
        PaymentRiskInvokeService riskInvokeService = mock(PaymentRiskInvokeService.class);
        PaymentRiskReservationCompensation compensation =
                new PaymentRiskReservationCompensation(riskInvokeService);
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setTransactionId("TX1001");
        PaymentRiskDecisionDTO decisionDTO = new PaymentRiskDecisionDTO();
        decisionDTO.setRiskRecordNo("RK1001");
        decisionDTO.setMerchantLimitReserved(true);
        TransactionSynchronizationManager.initSynchronization();

        compensation.register(commandDTO, decisionDTO);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(riskInvokeService, never()).cancelMerchantLimitReservation(
                commandDTO, decisionDTO, "payment local preparation rolled back");

        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(riskInvokeService).cancelMerchantLimitReservation(
                commandDTO, decisionDTO, "payment local preparation rolled back");
    }
}
