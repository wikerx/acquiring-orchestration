package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;
import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;
import lombok.extern.slf4j.Slf4j;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : FollowUpChannelResultTransactionEventTests
 * @date : 2026-08-02 23:00
 * @email : scott_x@163.com
 * @description : 后续交易渠道结果事件回归测试，验证终态 CAS 与商户通知触发 Outbox 在同一事务编排中只写一次
 * @status : create
 */
@Slf4j
class FollowUpChannelResultTransactionEventTests {

    /** Capture 终态 CAS 只有首次成功时写状态变更事件。 */
    @Test
    void shouldSaveCaptureTerminalEventOnlyWhenStatusChanges() {
        log.info("测试 Capture 终态事件，关键输入: 首次 CAS 成功、重复 CAS 失败");
        TransactionRecordService recordService = recordServiceWithOperation();
        TransactionLifecycleEventService eventService = mock(TransactionLifecycleEventService.class);
        when(recordService.completeCaptureChannelResult(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(true, false);
        DefaultCaptureChannelResultTransactionService service =
                new DefaultCaptureChannelResultTransactionService(recordService, eventService);
        CapturePreparationResultDTO preparation = new CapturePreparationResultDTO();
        fillPreparation(preparation, "CAPTURE");

        service.recordCaptureChannelResult(preparation, new PaymentChannelInvokeResultDTO());
        service.recordCaptureChannelResult(preparation, new PaymentChannelInvokeResultDTO());

        verifyTerminalEvent(eventService, "CAPTURE");
        log.info("Capture 终态事件测试完成，结果: 仅写入一次状态变更事件");
    }

    /** Refund 终态 CAS 只有首次成功时写状态变更事件。 */
    @Test
    void shouldSaveRefundTerminalEventOnlyWhenStatusChanges() {
        log.info("测试 Refund 终态事件，关键输入: 首次 CAS 成功、重复 CAS 失败");
        TransactionRecordService recordService = recordServiceWithOperation();
        TransactionLifecycleEventService eventService = mock(TransactionLifecycleEventService.class);
        when(recordService.completeRefundChannelResult(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(true, false);
        DefaultRefundChannelResultTransactionService service =
                new DefaultRefundChannelResultTransactionService(recordService, eventService);
        RefundPreparationResultDTO preparation = new RefundPreparationResultDTO();
        fillPreparation(preparation, "REFUND");

        service.recordRefundChannelResult(preparation, new PaymentChannelInvokeResultDTO());
        service.recordRefundChannelResult(preparation, new PaymentChannelInvokeResultDTO());

        verifyTerminalEvent(eventService, "REFUND");
        log.info("Refund 终态事件测试完成，结果: 仅写入一次状态变更事件");
    }

    /** Void 终态 CAS 只有首次成功时写状态变更事件。 */
    @Test
    void shouldSaveVoidTerminalEventOnlyWhenStatusChanges() {
        log.info("测试 Void 终态事件，关键输入: 首次 CAS 成功、重复 CAS 失败");
        TransactionRecordService recordService = recordServiceWithOperation();
        TransactionLifecycleEventService eventService = mock(TransactionLifecycleEventService.class);
        when(recordService.completeVoidChannelResult(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(true, false);
        DefaultVoidChannelResultTransactionService service =
                new DefaultVoidChannelResultTransactionService(recordService, eventService);
        VoidPreparationResultDTO preparation = new VoidPreparationResultDTO();
        fillPreparation(preparation, "VOID");

        service.recordVoidChannelResult(preparation, new PaymentChannelInvokeResultDTO());
        service.recordVoidChannelResult(preparation, new PaymentChannelInvokeResultDTO());

        verifyTerminalEvent(eventService, "VOID");
        log.info("Void 终态事件测试完成，结果: 仅写入一次状态变更事件");
    }

    /** Incremental Authorization 终态 CAS 只有首次成功时写状态变更事件。 */
    @Test
    void shouldSaveIncrementalAuthorizationTerminalEventOnlyWhenStatusChanges() {
        log.info("测试增额授权终态事件，关键输入: 首次 CAS 成功、重复 CAS 失败");
        TransactionRecordService recordService = recordServiceWithOperation();
        TransactionLifecycleEventService eventService = mock(TransactionLifecycleEventService.class);
        when(recordService.completeIncrementalAuthorizationChannelResult(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(true, false);
        DefaultIncrementalAuthorizationChannelResultTransactionService service =
                new DefaultIncrementalAuthorizationChannelResultTransactionService(recordService, eventService);
        IncrementalAuthorizationPreparationResultDTO preparation =
                new IncrementalAuthorizationPreparationResultDTO();
        fillPreparation(preparation, "INCREMENTAL_AUTHORIZATION");

        service.recordIncrementalAuthorizationChannelResult(
                preparation, new PaymentChannelInvokeResultDTO());
        service.recordIncrementalAuthorizationChannelResult(
                preparation, new PaymentChannelInvokeResultDTO());

        verifyTerminalEvent(eventService, "INCREMENTAL_AUTHORIZATION");
        log.info("增额授权终态事件测试完成，结果: 仅写入一次状态变更事件");
    }

    /** 构造可按真实交易时间定位动作单的记录服务。 */
    private TransactionRecordService recordServiceWithOperation() {
        TransactionRecordService recordService = mock(TransactionRecordService.class);
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setTransactionId("TX1001");
        when(recordService.findSourceOperationByTransactionId("TX1001", transactionDateTime()))
                .thenReturn(operationDO);
        return recordService;
    }

    /** 填充不同后续交易准备结果共享的事件身份。 */
    private void fillPreparation(Object preparation, String transactionType) {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId("M200001");
        command.setMerchantOrderNo("ORDER-1");
        command.setTransactionDateTime(transactionDateTime());
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setTransactionId("TX1001");
        result.setOperationId("OP1001");
        result.setTransactionType(transactionType);
        result.setStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        if (preparation instanceof CapturePreparationResultDTO target) {
            target.setCommandDTO(command);
            target.setResultDTO(result);
        } else if (preparation instanceof RefundPreparationResultDTO target) {
            target.setCommandDTO(command);
            target.setResultDTO(result);
        } else if (preparation instanceof VoidPreparationResultDTO target) {
            target.setCommandDTO(command);
            target.setResultDTO(result);
        } else if (preparation instanceof IncrementalAuthorizationPreparationResultDTO target) {
            target.setCommandDTO(command);
            target.setResultDTO(result);
        }
    }

    /** 验证同一终态只产生一次可精确路由的事件。 */
    private void verifyTerminalEvent(TransactionLifecycleEventService eventService,
                                     String transactionType) {
        verify(eventService, times(1)).saveStatusChanged(
                "TX1001",
                "OP1001",
                "M200001",
                "ORDER-1",
                transactionType,
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                transactionDateTime());
        verify(eventService, never()).saveStatusChanged(
                "TX1001",
                "OP1001",
                "M200001",
                "ORDER-1",
                transactionType,
                PaymentTransactionStatusEnum.FAILED.getCode(),
                transactionDateTime());
    }

    /** 返回固定毫秒级分片时间，证明事件不依赖交易号解析。 */
    private LocalDateTime transactionDateTime() {
        return LocalDateTime.of(2026, 8, 2, 23, 0, 0, 417_000_000);
    }
}
