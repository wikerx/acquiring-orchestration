package com.scott.payment.payment.service;

import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.payment.domain.refund.RefundExecutionOutcomeEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.mapper.TransactionChannelRequestMapper;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundExecutionServiceTests
 * @date : 2026-08-06 00:00
 * @description : 退款 MQ 执行幂等测试，验证首次抢占、重复消费转 QUERY 和终态保护。
 * @status : create
 */
class RefundExecutionServiceTests {

    @Test
    void waitingExecutionWithInitRequestIsClaimedOnceAndExecuted() {
        Fixture fixture = new Fixture();
        TransactionOperationDO waiting = fixture.operation(
                PaymentTransactionStatusEnum.PENDING.getCode(),
                PaymentProcessStageEnum.WAITING_EXECUTION.getCode(), 4);
        TransactionOperationDO claimed = fixture.operation(
                PaymentTransactionStatusEnum.PROCESSING.getCode(),
                PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode(), 5);
        TransactionChannelRequestDO request = fixture.request("INIT");
        when(fixture.operationMapper.selectByTransactionId("RT1001", fixture.transactionTime))
                .thenReturn(waiting, claimed);
        when(fixture.requestMapper.selectOriginalByTransaction("RT1001", "MPGS", fixture.transactionTime))
                .thenReturn(request);
        when(fixture.operationMapper.claimApprovedRefundExecution(
                eq("RT1001"), eq(fixture.transactionTime), eq(4), any(LocalDateTime.class)))
                .thenReturn(1);

        RefundExecutionOutcomeEnum outcome = fixture.service.execute(fixture.message(4));

        assertThat(outcome).isEqualTo(RefundExecutionOutcomeEnum.EXECUTED);
        verify(fixture.channelExecutor).execute(eq(claimed), eq(request), any(RefundExecutionMessage.class));
        verify(fixture.channelMatchService, never()).matchOne(any(), any());
    }

    @Test
    void duplicateMessageDuringChannelRequestQueriesInsteadOfResendingRefund() {
        Fixture fixture = new Fixture();
        TransactionOperationDO processing = fixture.operation(
                PaymentTransactionStatusEnum.PROCESSING.getCode(),
                PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode(), 5);
        when(fixture.operationMapper.selectByTransactionId("RT1001", fixture.transactionTime))
                .thenReturn(processing);

        RefundExecutionOutcomeEnum outcome = fixture.service.execute(fixture.message(4));

        assertThat(outcome).isEqualTo(RefundExecutionOutcomeEnum.QUERY_TRIGGERED);
        verify(fixture.channelMatchService).matchOne("RT1001", fixture.transactionTime);
        verify(fixture.channelExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void nonInitRequestFactQueriesBeforeAnyRefundResend() {
        Fixture fixture = new Fixture();
        TransactionOperationDO waiting = fixture.operation(
                PaymentTransactionStatusEnum.PENDING.getCode(),
                PaymentProcessStageEnum.WAITING_EXECUTION.getCode(), 4);
        when(fixture.operationMapper.selectByTransactionId("RT1001", fixture.transactionTime))
                .thenReturn(waiting);
        when(fixture.requestMapper.selectOriginalByTransaction("RT1001", "MPGS", fixture.transactionTime))
                .thenReturn(fixture.request("TIMEOUT"));

        RefundExecutionOutcomeEnum outcome = fixture.service.execute(fixture.message(4));

        assertThat(outcome).isEqualTo(RefundExecutionOutcomeEnum.QUERY_TRIGGERED);
        verify(fixture.operationMapper, never()).claimApprovedRefundExecution(any(), any(), any(), any());
        verify(fixture.channelExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void terminalRefundIgnoresDuplicateMessage() {
        Fixture fixture = new Fixture();
        TransactionOperationDO terminal = fixture.operation(
                PaymentTransactionStatusEnum.SUCCESS.getCode(),
                PaymentProcessStageEnum.FINISHED.getCode(), 7);
        when(fixture.operationMapper.selectByTransactionId("RT1001", fixture.transactionTime))
                .thenReturn(terminal);

        assertThat(fixture.service.execute(fixture.message(4)))
                .isEqualTo(RefundExecutionOutcomeEnum.IGNORED);

        verify(fixture.channelExecutor, never()).execute(any(), any(), any());
        verify(fixture.channelMatchService, never()).matchOne(any(), any());
    }

    private static final class Fixture {

        private final LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 6, 10, 0);
        private final TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        private final TransactionChannelRequestMapper requestMapper = mock(TransactionChannelRequestMapper.class);
        private final ApprovedRefundChannelExecutor channelExecutor = mock(ApprovedRefundChannelExecutor.class);
        private final TransactionChannelMatchService channelMatchService = mock(TransactionChannelMatchService.class);
        private final RefundExecutionService service = new RefundExecutionService(
                operationMapper, requestMapper, channelExecutor, channelMatchService);

        private RefundExecutionMessage message(int expectedVersion) {
            RefundExecutionMessage message = new RefundExecutionMessage();
            message.setMessageId("RE1001");
            message.setApprovalId("RA1001");
            message.setRefundTransactionId("RT1001");
            message.setRefundTransactionDateTime(transactionTime);
            message.setExpectedOperationVersion(expectedVersion);
            message.setEventType("REFUND_EXECUTION_REQUESTED");
            return message;
        }

        private TransactionOperationDO operation(String status, String stage, int version) {
            TransactionOperationDO operation = new TransactionOperationDO();
            operation.setTransactionId("RT1001");
            operation.setOperationId("OP1001");
            operation.setTransactionType("REFUND");
            operation.setTransactionStatus(status);
            operation.setProcessStage(stage);
            operation.setChannelCode("MPGS");
            operation.setTransactionDateTime(transactionTime);
            operation.setVersion(version);
            return operation;
        }

        private TransactionChannelRequestDO request(String status) {
            TransactionChannelRequestDO request = new TransactionChannelRequestDO();
            request.setRequestId("CR1001");
            request.setTransactionId("RT1001");
            request.setRequestStatus(status);
            request.setTransactionDateTime(transactionTime);
            return request;
        }
    }
}
