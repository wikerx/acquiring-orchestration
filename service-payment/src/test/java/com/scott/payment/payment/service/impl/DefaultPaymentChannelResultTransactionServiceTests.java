package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 渠道同步结果事务测试，验证交易状态持久化与生命周期 Outbox 在同一事务编排中发生。
 */
class DefaultPaymentChannelResultTransactionServiceTests {

    @Test
    void shouldSaveTerminalOutboxOnlyWhenPaymentCasChangedState() {
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        TransactionLifecycleEventService eventService =
                mock(TransactionLifecycleEventService.class);
        when(recordService.completeInitialChannelResultAndReport(
                any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(true, false);
        DefaultPaymentChannelResultTransactionService service =
                new DefaultPaymentChannelResultTransactionService(recordService, eventService);
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId("M200001");
        command.setMerchantOrderNo("ORDER-1");
        command.setTransactionDateTime(LocalDateTime.of(2026, 7, 30, 10, 0));
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setTransactionId("TX1001");
        result.setOperationId("OP1001");
        result.setTransactionType("PAYMENT");
        result.setStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        PaymentChannelInvokeResultDTO invokeResult = new PaymentChannelInvokeResultDTO();

        service.recordInitialChannelResult(
                command, null, invokeResult, result, PaymentRiskDecisionEnum.PASS, 2);
        service.recordInitialChannelResult(
                command, null, invokeResult, result, PaymentRiskDecisionEnum.PASS, 2);
        verify(eventService, times(1)).saveStatusChanged(
                "TX1001",
                "OP1001",
                "M200001",
                "ORDER-1",
                "PAYMENT",
                "SUCCESS",
                LocalDateTime.of(2026, 7, 30, 10, 0));
        verify(eventService, never()).saveStatusChanged(
                "TX1001",
                "OP1001",
                "M200001",
                "ORDER-1",
                "PAYMENT",
                "FAILED",
                LocalDateTime.of(2026, 7, 30, 10, 0));
    }
}
