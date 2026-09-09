package com.scott.payment.payment.service;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.domain.refund.RefundApprovalStatusEnum;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionRefundApprovalDO;
import com.scott.payment.payment.mapper.TransactionFlowEventMapper;
import com.scott.payment.payment.mapper.TransactionOperationMapper;
import com.scott.payment.payment.mapper.TransactionRefundApprovalMapper;
import com.scott.payment.payment.service.dto.RefundApprovalDecisionCommandDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalWorkflowServiceTests
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 退款审批工作流行为测试，覆盖决策幂等、自审限制、动作 CAS 和执行 Outbox 原子编排。
 * @status : create
 */
class RefundApprovalWorkflowServiceTests {

    @Test
    void approvingPendingRefundAdvancesActionAndCreatesExecutionOutbox() {
        Fixture fixture = new Fixture();
        TransactionRefundApprovalDO approval = fixture.pendingApproval("MERCHANT", "merchant-user-1");
        TransactionOperationDO operation = fixture.pendingOperation();
        when(fixture.approvalMapper.selectByApprovalIdForUpdate("RA1001")).thenReturn(approval);
        when(fixture.operationMapper.selectByTransactionId("RT1001", approval.getRefundTransactionDateTime()))
                .thenReturn(operation);
        when(fixture.operationMapper.approveRefundExecution(
                eq("RT1001"), eq(approval.getRefundTransactionDateTime()), eq(2), any(LocalDateTime.class)))
                .thenReturn(1);
        when(fixture.approvalMapper.decide(
                eq("RA1001"), eq(3), eq(RefundApprovalStatusEnum.APPROVED.getCode()),
                eq("admin-2"), eq("Reviewer"), eq("approved"), eq("DEC-1"),
                any(String.class), any(LocalDateTime.class)))
                .thenReturn(1);

        TransactionRefundApprovalDO result = fixture.service.approve(
                fixture.decision("RA1001", "DEC-1", "admin-2", "Reviewer", "approved"));

        assertThat(result.getApprovalStatus()).isEqualTo(RefundApprovalStatusEnum.APPROVED.getCode());
        assertThat(result.getExecutionEventId()).isNotBlank();
        verify(fixture.outboxService).save(any());
        verify(fixture.flowEventMapper).insertLogical(any());
    }

    @Test
    void repeatedSameApprovalDecisionReturnsExistingResultWithoutSideEffects() {
        Fixture fixture = new Fixture();
        TransactionRefundApprovalDO approval = fixture.pendingApproval("MERCHANT", "merchant-user-1");
        approval.setApprovalStatus(RefundApprovalStatusEnum.APPROVED.getCode());
        approval.setDecisionRequestId("DEC-1");
        approval.setExecutionEventId("RE1001");
        when(fixture.approvalMapper.selectByApprovalIdForUpdate("RA1001")).thenReturn(approval);

        TransactionRefundApprovalDO result = fixture.service.approve(
                fixture.decision("RA1001", "DEC-1", "admin-2", "Reviewer", "approved"));

        assertThat(result.getExecutionEventId()).isEqualTo("RE1001");
        verify(fixture.operationMapper, never()).approveRefundExecution(any(), any(), any(), any());
        verify(fixture.outboxService, never()).save(any());
    }

    @Test
    void adminApplicantCannotApproveOwnRefund() {
        Fixture fixture = new Fixture();
        TransactionRefundApprovalDO approval = fixture.pendingApproval("ADMIN", "admin-2");
        when(fixture.approvalMapper.selectByApprovalIdForUpdate("RA1001")).thenReturn(approval);

        assertThatThrownBy(() -> fixture.service.approve(
                fixture.decision("RA1001", "DEC-1", "admin-2", "Reviewer", "approved")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("own refund");

        verify(fixture.operationMapper, never()).approveRefundExecution(any(), any(), any(), any());
        verify(fixture.outboxService, never()).save(any());
    }

    @Test
    void rejectingPendingRefundTerminatesOnlyTheRefundAction() {
        Fixture fixture = new Fixture();
        TransactionRefundApprovalDO approval = fixture.pendingApproval("ADMIN", "admin-1");
        TransactionOperationDO operation = fixture.pendingOperation();
        when(fixture.approvalMapper.selectByApprovalIdForUpdate("RA1001")).thenReturn(approval);
        when(fixture.operationMapper.selectByTransactionId("RT1001", approval.getRefundTransactionDateTime()))
                .thenReturn(operation);
        when(fixture.approvalMapper.decide(
                eq("RA1001"), eq(3), eq(RefundApprovalStatusEnum.REJECTED.getCode()),
                eq("admin-2"), eq("Reviewer"), eq("risk rejected"), eq("DEC-2"),
                eq(null), any(LocalDateTime.class)))
                .thenReturn(1);
        when(fixture.transactionRecordService.terminateRefundBeforeChannel(
                eq(operation), eq("REFUND_APPROVAL_REJECTED"), eq("risk rejected"),
                eq("REFUND_APPROVAL"), eq("RA1001"), eq("ADMIN"), eq("admin-2"), any(LocalDateTime.class)))
                .thenReturn(true);

        TransactionRefundApprovalDO result = fixture.service.reject(
                fixture.decision("RA1001", "DEC-2", "admin-2", "Reviewer", "risk rejected"));

        assertThat(result.getApprovalStatus()).isEqualTo(RefundApprovalStatusEnum.REJECTED.getCode());
        verify(fixture.transactionRecordService).terminateRefundBeforeChannel(
                eq(operation), eq("REFUND_APPROVAL_REJECTED"), eq("risk rejected"),
                eq("REFUND_APPROVAL"), eq("RA1001"), eq("ADMIN"), eq("admin-2"), any(LocalDateTime.class));
        verify(fixture.outboxService, never()).save(any());
    }

    @Test
    void approvedWaitingExecutionRecoversTheOriginalExecutionEvent() {
        Fixture fixture = new Fixture();
        TransactionRefundApprovalDO approval = fixture.pendingApproval("MERCHANT", "merchant-user-1");
        approval.setApprovalStatus(RefundApprovalStatusEnum.APPROVED.getCode());
        approval.setExecutionEventId("RE1001");
        TransactionOperationDO operation = fixture.pendingOperation();
        operation.setProcessStage(PaymentProcessStageEnum.WAITING_EXECUTION.getCode());
        when(fixture.approvalMapper.selectByApprovalIdForUpdate("RA1001")).thenReturn(approval);
        when(fixture.operationMapper.selectByTransactionId("RT1001", approval.getRefundTransactionDateTime()))
                .thenReturn(operation);
        when(fixture.outboxService.recoverForRedelivery(
                eq("RE1001"), eq(approval.getRefundTransactionDateTime()),
                eq("REFUND_EXECUTION_REQUESTED"), any(LocalDateTime.class)))
                .thenReturn(true);

        assertThat(fixture.service.recoverApprovedExecution("RA1001", LocalDateTime.now())).isTrue();

        verify(fixture.outboxService, never()).save(any());
    }

    @Test
    void approvedChannelRequestingRefundIsNeverRedeliveredByRecovery() {
        Fixture fixture = new Fixture();
        TransactionRefundApprovalDO approval = fixture.pendingApproval("MERCHANT", "merchant-user-1");
        approval.setApprovalStatus(RefundApprovalStatusEnum.APPROVED.getCode());
        approval.setExecutionEventId("RE1001");
        TransactionOperationDO operation = fixture.pendingOperation();
        operation.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        operation.setProcessStage(PaymentProcessStageEnum.CHANNEL_REQUESTING.getCode());
        when(fixture.approvalMapper.selectByApprovalIdForUpdate("RA1001")).thenReturn(approval);
        when(fixture.operationMapper.selectByTransactionId("RT1001", approval.getRefundTransactionDateTime()))
                .thenReturn(operation);

        assertThat(fixture.service.recoverApprovedExecution("RA1001", LocalDateTime.now())).isFalse();

        verify(fixture.outboxService, never()).recoverForRedelivery(any(), any(), any(), any());
        verify(fixture.outboxService, never()).save(any());
    }

    private static final class Fixture {

        private final TransactionRefundApprovalMapper approvalMapper = mock(TransactionRefundApprovalMapper.class);
        private final TransactionOperationMapper operationMapper = mock(TransactionOperationMapper.class);
        private final TransactionFlowEventMapper flowEventMapper = mock(TransactionFlowEventMapper.class);
        private final TransactionEventOutboxService outboxService = mock(TransactionEventOutboxService.class);
        private final TransactionRecordService transactionRecordService = mock(TransactionRecordService.class);
        private final RefundApprovalWorkflowService service = new RefundApprovalWorkflowService(
                approvalMapper, operationMapper, flowEventMapper, outboxService, transactionRecordService);

        private Fixture() {
            when(flowEventMapper.insertLogical(any())).thenReturn(1);
        }

        private TransactionRefundApprovalDO pendingApproval(String applicantType, String applicantId) {
            TransactionRefundApprovalDO approval = new TransactionRefundApprovalDO();
            approval.setApprovalId("RA1001");
            approval.setRefundTransactionId("RT1001");
            approval.setRefundTransactionDateTime(LocalDateTime.of(2026, 8, 6, 10, 0));
            approval.setSourceTransactionId("PT1001");
            approval.setSourceTransactionDateTime(LocalDateTime.of(2026, 8, 5, 9, 0));
            approval.setRootTransactionDateTime(LocalDateTime.of(2026, 8, 5, 9, 0));
            approval.setMerchantId("M1001");
            approval.setApprovalStatus(RefundApprovalStatusEnum.PENDING.getCode());
            approval.setApplicantType(applicantType);
            approval.setApplicantId(applicantId);
            approval.setVersion(3);
            return approval;
        }

        private TransactionOperationDO pendingOperation() {
            TransactionOperationDO operation = new TransactionOperationDO();
            operation.setTransactionId("RT1001");
            operation.setOperationId("OP1001");
            operation.setMerchantId("M1001");
            operation.setMerchantOrderNo("ORDER-1");
            operation.setTransactionType("REFUND");
            operation.setTransactionStatus(PaymentTransactionStatusEnum.PENDING.getCode());
            operation.setProcessStage(PaymentProcessStageEnum.WAITING_APPROVAL.getCode());
            operation.setTransactionDateTime(LocalDateTime.of(2026, 8, 6, 10, 0));
            operation.setVersion(2);
            return operation;
        }

        private RefundApprovalDecisionCommandDTO decision(String approvalId,
                                                          String decisionRequestId,
                                                          String operatorId,
                                                          String operatorName,
                                                          String reason) {
            RefundApprovalDecisionCommandDTO command = new RefundApprovalDecisionCommandDTO();
            command.setApprovalId(approvalId);
            command.setDecisionRequestId(decisionRequestId);
            command.setExpectedVersion(3);
            command.setOperatorId(operatorId);
            command.setOperatorName(operatorName);
            command.setReason(reason);
            return command;
        }
    }
}
