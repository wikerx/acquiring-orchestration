package com.scott.payment.settlement.application;

import com.scott.payment.settlement.dto.SettlementCommandAudit;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReviewDecisionModels.TaskResult;
import com.scott.payment.settlement.entity.SettlementReviewDecisionRecoveryAuditDO;
import com.scott.payment.settlement.entity.SettlementReviewDecisionTaskDO;
import com.scott.payment.settlement.entity.SettlementReviewOrderDO;
import com.scott.payment.settlement.exception.SettlementReviewDecisionProcessingException;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewDecisionTaskMapper;
import com.scott.payment.settlement.mapper.SettlementReviewOrderMapper;
import com.scott.payment.settlement.mapper.SettlementReviewRateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewSegmentMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.service.SettlementClearingFactService;
import com.scott.payment.settlement.service.SettlementResultCalculationService;
import com.scott.payment.settlement.support.SettlementReviewFingerprintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证失败的分段复核任务只能在进度一致时原地恢复，并保持请求幂等。 */
class SettlementReviewDecisionTransactionServiceTest {

    private static final String TASK_NO = "DT" + "a".repeat(32);
    private static final String REVIEW_ORDER_NO = "SO20260908-00000001";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 10, 0);

    private SettlementReviewDecisionTaskMapper taskMapper;
    private SettlementReviewOrderMapper orderMapper;
    private SettlementReviewSegmentMapper segmentMapper;
    private SettlementBatchMapper batchMapper;
    private SettlementReviewDecisionTransactionService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(SettlementReviewDecisionTaskMapper.class);
        orderMapper = mock(SettlementReviewOrderMapper.class);
        segmentMapper = mock(SettlementReviewSegmentMapper.class);
        batchMapper = mock(SettlementBatchMapper.class);
        service = new SettlementReviewDecisionTransactionService(
                taskMapper,
                orderMapper,
                segmentMapper,
                mock(SettlementReviewCandidateMapper.class),
                mock(SettlementReviewRateMapper.class),
                mock(SettlementCandidateMapper.class),
                mock(MerchantSettlementProfileMapper.class),
                mock(SettlementBatchCreationService.class),
                batchMapper,
                mock(SettlementBatchCandidateMapper.class),
                mock(SettlementBatchRateMapper.class),
                mock(SettlementClearingFactService.class),
                mock(SettlementResultCalculationService.class),
                mock(SettlementReviewFingerprintService.class),
                Clock.fixed(Instant.parse("2026-09-08T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldResumeFailedTaskWithoutResettingCommittedProgress() {
        SettlementReviewDecisionTaskDO task = failedTask();
        stubConsistentApprovalState(task);
        when(taskMapper.insertRecoveryAuditIdempotent(any())).thenReturn(1);
        when(taskMapper.selectRecoveryAuditByRequestKeyForUpdate("RESUME-1"))
                .thenReturn(null, recoveryAudit("RESUME-1", 7L));
        when(taskMapper.resumeFailed(TASK_NO, 7L, NOW)).thenReturn(1);

        TaskResult result = service.resume(TASK_NO, 7L, recoveryCommand("RESUME-1"));

        assertThat(result.taskStatus()).isEqualTo("QUEUED");
        assertThat(result.processedSegmentCount()).isEqualTo(1);
        assertThat(result.resultBatchCount()).isEqualTo(1);
        assertThat(result.version()).isEqualTo(8L);
        assertThat(result.recoverable()).isFalse();
        verify(taskMapper).resumeFailed(TASK_NO, 7L, NOW);
    }

    @Test
    void shouldReplaySameRecoveryRequestWithoutResumingTwice() {
        SettlementReviewDecisionTaskDO resumed = failedTask();
        resumed.setTaskStatus("QUEUED");
        resumed.setVersion(8L);
        when(taskMapper.selectRecoveryAuditByRequestKey("RESUME-1"))
                .thenReturn(recoveryAudit("RESUME-1", 7L));
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(resumed);

        TaskResult result = service.resume(TASK_NO, 7L, recoveryCommand("RESUME-1"));

        assertThat(result.taskStatus()).isEqualTo("QUEUED");
        assertThat(result.version()).isEqualTo(8L);
        verify(taskMapper, never()).selectByTaskNoForUpdate(any());
        verify(taskMapper, never()).resumeFailed(any(), any(Long.class), any());
    }

    @Test
    void shouldRejectRecoveryWhenTaskVersionIsStale() {
        SettlementReviewDecisionTaskDO task = failedTask();
        when(taskMapper.selectByTaskNoForUpdate(TASK_NO)).thenReturn(task);

        assertThatThrownBy(() -> service.resume(TASK_NO, 6L, recoveryCommand("RESUME-2")))
                .isInstanceOf(SettlementReviewDecisionProcessingException.class)
                .hasMessageContaining("version is stale");

        verify(taskMapper, never()).insertRecoveryAuditIdempotent(any());
        verify(taskMapper, never()).resumeFailed(any(), any(Long.class), any());
    }

    @Test
    void shouldRejectRecoveryWhenSegmentProgressIsInconsistent() {
        SettlementReviewDecisionTaskDO task = failedTask();
        when(taskMapper.selectByTaskNoForUpdate(TASK_NO)).thenReturn(task);
        when(orderMapper.selectByReviewOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(pendingOrder());
        when(segmentMapper.countByOrderNo(REVIEW_ORDER_NO)).thenReturn(3);
        when(segmentMapper.countByOrderNoAndStatus(REVIEW_ORDER_NO, "LOCKED")).thenReturn(1);
        when(segmentMapper.countByOrderNoAndStatus(REVIEW_ORDER_NO, "CONSUMED")).thenReturn(1);
        when(segmentMapper.countByOrderNoAndStatus(REVIEW_ORDER_NO, "RELEASED")).thenReturn(0);

        assertThatThrownBy(() -> service.resume(TASK_NO, 7L, recoveryCommand("RESUME-3")))
                .isInstanceOf(SettlementReviewDecisionProcessingException.class)
                .hasMessageContaining("segment progress is inconsistent");

        verify(taskMapper, never()).insertRecoveryAuditIdempotent(any());
        verify(taskMapper, never()).resumeFailed(any(), any(Long.class), any());
    }

    @Test
    void shouldNotAdvertiseUnknownFailureAsRecoverable() {
        SettlementReviewDecisionTaskDO task = failedTask();
        task.setLastFailureCode("SETTLEMENT_REVIEW_DECISION_NEW_PERMANENT_FAILURE");
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task);

        TaskResult result = service.get(TASK_NO);

        assertThat(result.recoverable()).isFalse();
    }

    @Test
    void shouldNotAdvertiseUnexpectedFailureAsRecoverable() {
        SettlementReviewDecisionTaskDO task = failedTask();
        task.setLastFailureCode("SETTLEMENT_REVIEW_DECISION_UNEXPECTED_FAILURE");
        when(taskMapper.selectByTaskNo(TASK_NO)).thenReturn(task);

        TaskResult result = service.get(TASK_NO);

        assertThat(result.recoverable()).isFalse();
    }

    private void stubConsistentApprovalState(SettlementReviewDecisionTaskDO task) {
        when(taskMapper.selectByTaskNoForUpdate(TASK_NO)).thenReturn(task);
        when(orderMapper.selectByReviewOrderNoForUpdate(REVIEW_ORDER_NO)).thenReturn(pendingOrder());
        when(segmentMapper.countByOrderNo(REVIEW_ORDER_NO)).thenReturn(3);
        when(segmentMapper.countByOrderNoAndStatus(REVIEW_ORDER_NO, "LOCKED")).thenReturn(2);
        when(segmentMapper.countByOrderNoAndStatus(REVIEW_ORDER_NO, "CONSUMED")).thenReturn(1);
        when(segmentMapper.countByOrderNoAndStatus(REVIEW_ORDER_NO, "RELEASED")).thenReturn(0);
        when(batchMapper.countAsyncApprovedReviewBatches(REVIEW_ORDER_NO)).thenReturn(1);
    }

    private SettlementReviewDecisionTaskDO failedTask() {
        SettlementReviewDecisionTaskDO task = new SettlementReviewDecisionTaskDO();
        task.setTaskNo(TASK_NO);
        task.setRequestKey("DECISION-1");
        task.setReviewOrderNo(REVIEW_ORDER_NO);
        task.setExpectedReviewVersion(5L);
        task.setDecisionAction("APPROVE");
        task.setDecisionComment("approve frozen segments");
        task.setOperatorAccountId(99L);
        task.setOperatorAccountName("Checker");
        task.setOperatorRoleSnapshot("SETTLEMENT_CHECKER");
        task.setClientIp("10.0.0.2");
        task.setUserAgent("JUnit");
        task.setOperationTime(NOW.minusMinutes(30));
        task.setTotalSegmentCount(3);
        task.setProcessedSegmentCount(1);
        task.setResultBatchCount(1);
        task.setFirstSettlementBatchNo("SB20260908-00000001");
        task.setTaskStatus("FAILED");
        task.setRetryCount(4);
        task.setLastFailureCode("SETTLEMENT_REVIEW_DECISION_CANDIDATE_CONSUME_FAILED");
        task.setLastFailureMessage("temporary database failure");
        task.setStartedTime(NOW.minusMinutes(20));
        task.setCompletedTime(NOW.minusMinutes(1));
        task.setVersion(7L);
        task.setCreateTime(NOW.minusMinutes(30));
        task.setUpdateTime(NOW.minusMinutes(1));
        return task;
    }

    private SettlementReviewOrderDO pendingOrder() {
        SettlementReviewOrderDO order = new SettlementReviewOrderDO();
        order.setReviewOrderNo(REVIEW_ORDER_NO);
        order.setCreateMode("MANUAL_ASYNC");
        order.setReviewStatus("PENDING_APPROVAL");
        order.setVersion(5L);
        return order;
    }

    private SettlementCommandAudit recoveryCommand(String requestKey) {
        return new SettlementCommandAudit(requestKey, "resume after transient failure",
                new SettlementOperatorSnapshot(88L, "Settlement Operator", "SETTLEMENT_RECOVERY",
                        "10.0.0.8", "JUnit Admin", NOW));
    }

    private SettlementReviewDecisionRecoveryAuditDO recoveryAudit(String requestKey,
                                                                   long expectedVersion) {
        SettlementReviewDecisionRecoveryAuditDO audit = new SettlementReviewDecisionRecoveryAuditDO();
        audit.setTaskNo(TASK_NO);
        audit.setReviewOrderNo(REVIEW_ORDER_NO);
        audit.setRequestKey(requestKey);
        audit.setExpectedVersion(expectedVersion);
        audit.setTaskStatusBefore("FAILED");
        audit.setProcessedSegmentCountBefore(1);
        audit.setResultBatchCountBefore(1);
        audit.setRetryCountBefore(4);
        audit.setFailureCodeBefore("SETTLEMENT_REVIEW_DECISION_CANDIDATE_CONSUME_FAILED");
        audit.setStartedTimeBefore(NOW.minusMinutes(20));
        audit.setCompletedTimeBefore(NOW.minusMinutes(1));
        audit.setReason("resume after transient failure");
        audit.setOperatorAccountId(88L);
        return audit;
    }
}
