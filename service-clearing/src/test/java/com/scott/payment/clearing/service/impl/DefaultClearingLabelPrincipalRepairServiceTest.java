package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairDO;
import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairDependencyAuditDO;
import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairReserveStateAuditDO;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDO;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDependencyDO;
import com.scott.payment.clearing.entity.ClearingTransactionDetailDO;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingLabelPrincipalRepairMapper;
import com.scott.payment.clearing.mapper.ClearingLabelPrincipalRepairDependencyAuditMapper;
import com.scott.payment.clearing.mapper.ClearingLabelPrincipalRepairReserveStateAuditMapper;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.mapper.ClearingSettlementCandidateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionDetailMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService.RepairResult;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService.ReviewCommand;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService.ReviewDecision;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService.SubmitCommand;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService.LabelPrincipalRepairCandidateReplacement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证未结算历史标签本金修复只追加新修订，并受幂等、双人复核和全部冻结版本保护。
 */
class DefaultClearingLabelPrincipalRepairServiceTest {

    private ClearingLabelPrincipalRepairMapper repairMapper;
    private ClearingLabelPrincipalRepairDependencyAuditMapper dependencyAuditMapper;
    private ClearingLabelPrincipalRepairReserveStateAuditMapper reserveStateAuditMapper;
    private ClearingTransactionFinanceStateMapper financeStateMapper;
    private ClearingTransactionOperationMapper operationMapper;
    private ClearingTransactionDetailMapper detailMapper;
    private ClearingReserveMapper reserveMapper;
    private ClearingSettlementCandidateMapper candidateMapper;
    private ClearingSettlementCandidateService candidateService;
    private ClearingTransactionEventOutboxMapper outboxMapper;
    private DefaultClearingLabelPrincipalRepairService service;
    private ClearingTransactionFinanceStateDO state;
    private ClearingTransactionOperationDO operation;
    private ClearingSettlementCandidateDO candidate;
    private List<ClearingTransactionDetailDO> details;
    private AtomicReference<ClearingLabelPrincipalRepairDO> storedRequest;

    @BeforeEach
    void setUp() {
        repairMapper = mock(ClearingLabelPrincipalRepairMapper.class);
        dependencyAuditMapper = mock(ClearingLabelPrincipalRepairDependencyAuditMapper.class);
        reserveStateAuditMapper = mock(ClearingLabelPrincipalRepairReserveStateAuditMapper.class);
        financeStateMapper = mock(ClearingTransactionFinanceStateMapper.class);
        operationMapper = mock(ClearingTransactionOperationMapper.class);
        detailMapper = mock(ClearingTransactionDetailMapper.class);
        reserveMapper = mock(ClearingReserveMapper.class);
        candidateMapper = mock(ClearingSettlementCandidateMapper.class);
        candidateService = mock(ClearingSettlementCandidateService.class);
        outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        service = new DefaultClearingLabelPrincipalRepairService(
                repairMapper, dependencyAuditMapper, reserveStateAuditMapper, financeStateMapper,
                operationMapper, detailMapper, reserveMapper, candidateMapper, candidateService, outboxMapper);
        state = state();
        operation = operation();
        candidate = candidate();
        details = new ArrayList<>(List.of(principal(), fee()));
        storedRequest = new AtomicReference<>();
        stubRepairableFacts();
    }

    @Test
    void approveShouldAppendCorrectedPrincipalAndPreserveFeeFacts() {
        RepairResult submitted = submit(" maker ");
        stubApprovalWrites();

        RepairResult executed = service.review(review(
                submitted.repairNo(), submitted.version(), ReviewDecision.APPROVE, "checker"));

        assertThat(executed.status()).isEqualTo("EXECUTED");
        assertThat(executed.oldClearingRevision()).isEqualTo(2);
        assertThat(executed.newClearingRevision()).isEqualTo(3);
        assertThat(executed.newCandidateNo()).isEqualTo("SC-NEW");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClearingTransactionDetailDO>> rows = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertBatch(rows.capture());
        ClearingTransactionDetailDO repairedPrincipal = rows.getValue().get(0);
        ClearingTransactionDetailDO repairedFee = rows.getValue().get(1);
        assertThat(repairedPrincipal.getAmount()).isEqualByComparingTo("100.00");
        assertThat(repairedPrincipal.getCurrency()).isEqualTo("EUR");
        assertThat(repairedPrincipal.getCurrencyExponent()).isEqualTo(2);
        assertThat(repairedPrincipal.getBasisAmount()).isEqualByComparingTo("100.00");
        assertThat(repairedPrincipal.getBasisCurrency()).isEqualTo("EUR");
        assertThat(repairedPrincipal.getFormulaSnapshot()).contains("frozen label amount");
        assertThat(repairedPrincipal.getClearingRevision()).isEqualTo(3);
        assertThat(repairedPrincipal.getRecordStatus()).isEqualTo("ACTIVE");

        ClearingTransactionDetailDO originalFee = details.get(1);
        assertThat(repairedFee.getAmount()).isEqualByComparingTo(originalFee.getAmount());
        assertThat(repairedFee.getCurrency()).isEqualTo(originalFee.getCurrency());
        assertThat(repairedFee.getBasisAmount()).isEqualByComparingTo(originalFee.getBasisAmount());
        assertThat(repairedFee.getPercentageRate()).isEqualByComparingTo(originalFee.getPercentageRate());
        assertThat(repairedFee.getRuleSnapshotJson()).isEqualTo(originalFee.getRuleSnapshotJson());
        assertThat(repairedFee.getFeeSnapshotHash()).isEqualTo(originalFee.getFeeSnapshotHash());
        assertThat(repairedFee.getFormulaSnapshot()).isEqualTo(originalFee.getFormulaSnapshot());
        assertThat(repairedFee.getClearingRevision()).isEqualTo(3);

        verify(detailMapper).supersedeActiveRevision("TX-1", transactionTime(), 2,
                LocalDateTime.of(2026, 11, 6, 16, 0));
        verify(financeStateMapper).completeLabelPrincipalRepair(
                10L, "TX-1", transactionTime(), 7, 2, 3,
                LocalDateTime.of(2026, 11, 6, 16, 0));
        verify(candidateService).replaceForLabelPrincipalRepair(
                "FS-1", 2, 3, 20L, 3L, 0, LocalDateTime.of(2026, 11, 6, 16, 0));

        ArgumentCaptor<ClearingTransactionEventOutboxDO> outbox =
                ArgumentCaptor.forClass(ClearingTransactionEventOutboxDO.class);
        verify(outboxMapper).insertLogical(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("TRANSACTION_CLEARING_COMPLETED");
        assertThat(outbox.getValue().getAggregateType()).isEqualTo("TRANSACTION_CLEARING_REPAIR");
        assertThat(outbox.getValue().getPayloadJson())
                .contains("\"transactionId\":\"TX-1\"")
                .doesNotContain("sourceEventNo", "expectedClearingRevision");
    }

    @Test
    void approveShouldCopyHoldFactAndMigrateFrozenReserveDependency() {
        ClearingReserveDetailDO reserve = reserve();
        ClearingReserveStateDO reserveState = reserveState(reserve);
        ClearingSettlementCandidateDependencyDO dependency = dependency();
        ClearingSettlementCandidateDO dependent = dependentCandidate();
        when(reserveMapper.selectActiveRevision("TX-1", transactionTime(), 2))
                .thenReturn(List.of(reserve));
        when(reserveMapper.selectStateForUpdate("TX-1", transactionTime()))
                .thenReturn(reserveState);
        when(candidateMapper.selectDependencyReferencesForUpdate(20L))
                .thenReturn(List.of(dependency));
        when(candidateMapper.selectByIdForUpdate(40L)).thenReturn(dependent);
        RepairResult submitted = submit("maker");
        stubApprovalWrites();
        when(candidateService.replaceForLabelPrincipalRepair(
                "FS-1", 2, 3, 20L, 3L, 1, LocalDateTime.of(2026, 11, 6, 16, 0)))
                .thenReturn(new LabelPrincipalRepairCandidateReplacement(20L, 21L, "SC-NEW"));
        when(reserveMapper.insertDetail(any())).thenReturn(1);
        when(reserveMapper.migrateHoldReference(anyLong(), anyString(), anyString(), any(),
                anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(reserveStateAuditMapper.insert(any())).thenReturn(1);
        when(reserveMapper.supersedeActiveRevision(
                "TX-1", transactionTime(), 2, LocalDateTime.of(2026, 11, 6, 16, 0)))
                .thenReturn(1);
        when(candidateMapper.migrateReserveHoldDependency(50L, 40L, 20L, 21L, 6L))
                .thenReturn(1);
        when(dependencyAuditMapper.insert(any())).thenReturn(1);

        RepairResult executed = service.review(review(
                submitted.repairNo(), submitted.version(), ReviewDecision.APPROVE, "checker"));

        assertThat(executed.status()).isEqualTo("EXECUTED");
        ArgumentCaptor<ClearingReserveDetailDO> reserveCaptor =
                ArgumentCaptor.forClass(ClearingReserveDetailDO.class);
        verify(reserveMapper).insertDetail(reserveCaptor.capture());
        ClearingReserveDetailDO copied = reserveCaptor.getValue();
        assertThat(copied.getId()).isNull();
        assertThat(copied.getReserveClearingDetailNo()).startsWith("RD").hasSize(34);
        assertThat(copied.getClearingRevision()).isEqualTo(3);
        assertThat(copied.getRecordStatus()).isEqualTo("ACTIVE");
        assertThat(copied.getReserveCurrency()).isEqualTo(reserve.getReserveCurrency());
        assertThat(copied.getBasisAmount()).isEqualByComparingTo(reserve.getBasisAmount());
        assertThat(copied.getReserveRate()).isEqualByComparingTo(reserve.getReserveRate());
        assertThat(copied.getRetainedAmount()).isEqualByComparingTo(reserve.getRetainedAmount());
        assertThat(copied.getRemainingAmount()).isEqualByComparingTo(reserve.getRemainingAmount());
        assertThat(copied.getReserveSnapshotHash()).isEqualTo(reserve.getReserveSnapshotHash());
        assertThat(copied.getFormulaSnapshot()).isEqualTo(reserve.getFormulaSnapshot());
        verify(reserveMapper).migrateHoldReference(
                31L, "RS-1", "TX-1", transactionTime(), "RD-OLD",
                copied.getReserveClearingDetailNo(), 4L, LocalDateTime.of(2026, 11, 6, 16, 0));

        ArgumentCaptor<ClearingLabelPrincipalRepairReserveStateAuditDO> reserveStateAuditCaptor =
                ArgumentCaptor.forClass(ClearingLabelPrincipalRepairReserveStateAuditDO.class);
        verify(reserveStateAuditMapper).insert(reserveStateAuditCaptor.capture());
        assertThat(reserveStateAuditCaptor.getValue().getRepairNo()).isEqualTo(submitted.repairNo());
        assertThat(reserveStateAuditCaptor.getValue().getOldHoldDetailNo()).isEqualTo("RD-OLD");
        assertThat(reserveStateAuditCaptor.getValue().getNewHoldDetailNo())
                .isEqualTo(copied.getReserveClearingDetailNo());
        assertThat(reserveStateAuditCaptor.getValue().getOldStateVersion()).isEqualTo(4L);
        assertThat(reserveStateAuditCaptor.getValue().getNewStateVersion()).isEqualTo(5L);

        verify(candidateService).replaceForLabelPrincipalRepair(
                "FS-1", 2, 3, 20L, 3L, 1, LocalDateTime.of(2026, 11, 6, 16, 0));
        verify(candidateMapper).migrateReserveHoldDependency(50L, 40L, 20L, 21L, 6L);
        ArgumentCaptor<ClearingLabelPrincipalRepairDependencyAuditDO> auditCaptor =
                ArgumentCaptor.forClass(ClearingLabelPrincipalRepairDependencyAuditDO.class);
        verify(dependencyAuditMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getRepairNo()).isEqualTo(submitted.repairNo());
        assertThat(auditCaptor.getValue().getDependencyId()).isEqualTo(50L);
        assertThat(auditCaptor.getValue().getOldDependsOnCandidateId()).isEqualTo(20L);
        assertThat(auditCaptor.getValue().getNewDependsOnCandidateId()).isEqualTo(21L);
        assertThat(auditCaptor.getValue().getDependencyType()).isEqualTo("RESERVE_HOLD_POSTED");
    }

    @Test
    void submitShouldUseNormalizedRequestKeyAndReturnExactDuplicate() {
        RepairResult first = submit("maker");

        RepairResult duplicate = service.submit(submitCommand("REQ-1", "TX-1", "maker"));

        assertThat(duplicate).isEqualTo(first);
        verify(repairMapper, times(1)).insertIdempotent(any());
        assertThat(storedRequest.get().getCreateTime())
                .isEqualTo(LocalDateTime.of(2026, 11, 6, 15, 0));
    }

    @Test
    void submitShouldRejectRequestKeyReusedForDifferentIdentity() {
        submit("maker");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.submit(submitCommand("REQ-1", "TX-OTHER", "maker")))
                .withMessageContaining("request key");
    }

    @Test
    void reviewShouldRejectSameMakerAndChecker() {
        RepairResult submitted = submit("maker");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(review(
                        submitted.repairNo(), 0, ReviewDecision.APPROVE, "maker")))
                .withMessageContaining("must be different");
        verifyNoInteractions(candidateService, outboxMapper);
    }

    @Test
    void rejectShouldBeTerminalAndAllowOnlyIdenticalReplay() {
        RepairResult submitted = submit("maker");
        when(repairMapper.markRejected(anyString(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);
        ReviewCommand command = review(submitted.repairNo(), 0, ReviewDecision.REJECT, "checker");

        RepairResult rejected = service.review(command);
        assertThat(rejected.status()).isEqualTo("REJECTED");
        storedRequest.get().setRepairStatus("REJECTED");
        storedRequest.get().setReviewOperator("checker");
        storedRequest.get().setReviewComment("reviewed");
        storedRequest.get().setVersion(1L);
        assertThat(service.review(command).status()).isEqualTo("REJECTED");
        verify(repairMapper, times(1)).markRejected(
                anyString(), anyLong(), anyString(), anyString(), any());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(new ReviewCommand(
                        submitted.repairNo(), 1, ReviewDecision.APPROVE, "reviewed",
                        "checker", Instant.parse("2026-11-06T08:00:00Z"))))
                .withMessageContaining("different terminal decision");
    }

    @Test
    void reviewShouldRejectFrozenStateOrSnapshotDrift() {
        RepairResult submitted = submit("maker");
        state.setVersion(8);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(review(
                        submitted.repairNo(), 0, ReviewDecision.APPROVE, "checker")))
                .withMessageContaining("stale or ineligible");

        state.setVersion(7);
        details.get(1).setAmount(new BigDecimal("3.00"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(review(
                        submitted.repairNo(), 0, ReviewDecision.APPROVE, "checker")))
                .withMessageContaining("frozen facts have changed");
    }

    @Test
    void submitShouldRejectClaimedCandidateUnsupportedReserveDependencyAndAlreadyCorrectPrincipal() {
        candidate.setSettlementBatchNo("SB-1");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.submit(submitCommand("REQ-CLAIMED", "TX-1", "maker")))
                .withMessageContaining("already claimed");

        candidate.setSettlementBatchNo(null);
        ClearingSettlementCandidateDependencyDO unsupported = dependency();
        unsupported.setCandidateId(20L);
        unsupported.setDependsOnCandidateId(99L);
        when(candidateMapper.selectDependencyReferencesForUpdate(20L))
                .thenReturn(List.of(unsupported));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.submit(submitCommand("REQ-DEPENDENCY", "TX-1", "maker")))
                .withMessageContaining("dependency is unsupported");

        when(candidateMapper.selectDependencyReferencesForUpdate(20L)).thenReturn(List.of());
        when(reserveMapper.selectActiveRevision("TX-1", transactionTime(), 2))
                .thenReturn(List.of(new ClearingReserveDetailDO()));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.submit(submitCommand("REQ-RESERVE", "TX-1", "maker")))
                .withMessageContaining("reserve facts are inconsistent");

        when(reserveMapper.selectActiveRevision("TX-1", transactionTime(), 2)).thenReturn(List.of());
        ClearingTransactionDetailDO principal = details.get(0);
        principal.setAmount(principal.getLabelAmount());
        principal.setCurrency(principal.getLabelCurrency());
        principal.setCurrencyExponent(principal.getLabelCurrencyExponent());
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.submit(submitCommand("REQ-CORRECT", "TX-1", "maker")))
                .withMessageContaining("not required");
    }

    @Test
    void submitShouldRejectInconsistentReserveStateAndClaimedDependentCandidate() {
        ClearingReserveDetailDO reserve = reserve();
        when(reserveMapper.selectActiveRevision("TX-1", transactionTime(), 2))
                .thenReturn(List.of(reserve));
        when(reserveMapper.selectStateForUpdate("TX-1", transactionTime()))
                .thenReturn(new ClearingReserveStateDO());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.submit(submitCommand("REQ-RESERVE-STATE", "TX-1", "maker")))
                .withMessageContaining("reserve state is inconsistent");

        when(reserveMapper.selectStateForUpdate("TX-1", transactionTime()))
                .thenReturn(reserveState(reserve));
        ClearingSettlementCandidateDO dependent = dependentCandidate();
        dependent.setClaimedTime(LocalDateTime.of(2026, 11, 6, 14, 0));
        when(candidateMapper.selectDependencyReferencesForUpdate(20L))
                .thenReturn(List.of(dependency()));
        when(candidateMapper.selectByIdForUpdate(40L)).thenReturn(dependent);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.submit(submitCommand("REQ-CLAIMED-DEPENDENT", "TX-1", "maker")))
                .withMessageContaining("stale or claimed");
    }

    @Test
    void approveShouldStopBeforeAuditAndFinancialStateWhenReserveStateMigrationCasFails() {
        ClearingReserveDetailDO reserve = reserve();
        when(reserveMapper.selectActiveRevision("TX-1", transactionTime(), 2))
                .thenReturn(List.of(reserve));
        when(reserveMapper.selectStateForUpdate("TX-1", transactionTime()))
                .thenReturn(reserveState(reserve));
        RepairResult submitted = submit("maker");
        when(detailMapper.insertBatch(any())).thenReturn(2);
        when(reserveMapper.insertDetail(any())).thenReturn(1);
        when(reserveMapper.migrateHoldReference(anyLong(), anyString(), anyString(), any(),
                anyString(), anyString(), anyLong(), any())).thenReturn(0);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(review(
                        submitted.repairNo(), submitted.version(), ReviewDecision.APPROVE, "checker")))
                .withMessageContaining("reserve state migration CAS");

        verifyNoInteractions(reserveStateAuditMapper, candidateService, outboxMapper);
        verify(financeStateMapper, never()).completeLabelPrincipalRepair(
                anyLong(), anyString(), any(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void approveShouldStopBeforeAuditAndOutboxWhenDependencyMigrationCasFails() {
        when(reserveMapper.selectActiveRevision("TX-1", transactionTime(), 2))
                .thenReturn(List.of(reserve()));
        when(reserveMapper.selectStateForUpdate("TX-1", transactionTime()))
                .thenReturn(reserveState(reserve()));
        when(candidateMapper.selectDependencyReferencesForUpdate(20L))
                .thenReturn(List.of(dependency()));
        when(candidateMapper.selectByIdForUpdate(40L)).thenReturn(dependentCandidate());
        RepairResult submitted = submit("maker");
        stubApprovalWrites();
        when(candidateService.replaceForLabelPrincipalRepair(
                "FS-1", 2, 3, 20L, 3L, 1, LocalDateTime.of(2026, 11, 6, 16, 0)))
                .thenReturn(new LabelPrincipalRepairCandidateReplacement(20L, 21L, "SC-NEW"));
        when(reserveMapper.insertDetail(any())).thenReturn(1);
        when(reserveMapper.migrateHoldReference(anyLong(), anyString(), anyString(), any(),
                anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(reserveStateAuditMapper.insert(any())).thenReturn(1);
        when(reserveMapper.supersedeActiveRevision(
                "TX-1", transactionTime(), 2, LocalDateTime.of(2026, 11, 6, 16, 0)))
                .thenReturn(1);
        when(candidateMapper.migrateReserveHoldDependency(50L, 40L, 20L, 21L, 6L))
                .thenReturn(0);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(review(
                        submitted.repairNo(), submitted.version(), ReviewDecision.APPROVE, "checker")))
                .withMessageContaining("dependency migration CAS");

        verifyNoInteractions(dependencyAuditMapper, outboxMapper);
        verify(repairMapper, never()).markExecuted(
                anyString(), anyLong(), anyString(), anyString(), any(), anyInt(), anyString(),
                anyLong(), any());
    }

    @Test
    void approveShouldStopBeforeCandidateAndOutboxWhenFinanceCasFails() {
        RepairResult submitted = submit("maker");
        when(detailMapper.insertBatch(any())).thenReturn(2);
        when(detailMapper.supersedeActiveRevision("TX-1", transactionTime(), 2,
                LocalDateTime.of(2026, 11, 6, 16, 0)))
                .thenReturn(2);
        when(financeStateMapper.completeLabelPrincipalRepair(
                anyLong(), anyString(), any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(0);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(review(
                        submitted.repairNo(), 0, ReviewDecision.APPROVE, "checker")))
                .withMessageContaining("finance state CAS");
        verifyNoInteractions(candidateService, outboxMapper);
        verify(repairMapper, never()).markExecuted(
                anyString(), anyLong(), anyString(), anyString(), any(), anyInt(), anyString(),
                anyLong(), any());
    }

    @Test
    void submitShouldRejectLabelCurrencyExponentNotFrozenByOperation() {
        operation.setLabelCurrencyExponent(3);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.submit(submitCommand("REQ-EXPONENT", "TX-1", "maker")))
                .withMessageContaining("label snapshot is inconsistent");
    }

    @Test
    void submitShouldNotUseTransactionCurrencyExponentAsLabelExponent() {
        operation.setCurrencyExponent(0);

        when(repairMapper.selectByRequestKeyForUpdate("REQ-TRANSACTION-EXPONENT"))
                .thenReturn(null)
                .thenAnswer(invocation -> storedRequest.get());
        when(repairMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            storedRequest.set(invocation.getArgument(0));
            return 1;
        });

        RepairResult result = service.submit(
                submitCommand("REQ-TRANSACTION-EXPONENT", "TX-1", "maker"));

        assertThat(result.status()).isEqualTo("PENDING_REVIEW");
        assertThat(storedRequest.get().getLabelCurrencyExponent()).isEqualTo(2);
    }

    private RepairResult submit(String maker) {
        when(repairMapper.selectByRequestKeyForUpdate("REQ-1"))
                .thenReturn(null)
                .thenAnswer(invocation -> storedRequest.get());
        when(repairMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            storedRequest.set(invocation.getArgument(0));
            return 1;
        });
        RepairResult result = service.submit(submitCommand("  REQ-1  ", "TX-1", maker));
        when(repairMapper.selectByRequestKeyForUpdate("REQ-1"))
                .thenAnswer(invocation -> storedRequest.get());
        when(repairMapper.selectForUpdate(result.repairNo()))
                .thenAnswer(invocation -> storedRequest.get());
        return result;
    }

    private void stubApprovalWrites() {
        when(detailMapper.insertBatch(any())).thenAnswer(invocation ->
                ((List<?>) invocation.getArgument(0)).size());
        LocalDateTime now = LocalDateTime.of(2026, 11, 6, 16, 0);
        when(detailMapper.supersedeActiveRevision("TX-1", transactionTime(), 2, now))
                .thenReturn(2);
        when(financeStateMapper.completeLabelPrincipalRepair(
                10L, "TX-1", transactionTime(), 7, 2, 3, now)).thenReturn(1);
        when(candidateService.replaceForLabelPrincipalRepair(
                "FS-1", 2, 3, 20L, 3L, 0, now))
                .thenReturn(new LabelPrincipalRepairCandidateReplacement(20L, 21L, "SC-NEW"));
        when(outboxMapper.insertLogical(any())).thenReturn(1);
        when(repairMapper.markExecuted(
                anyString(), anyLong(), anyString(), anyString(), any(), anyInt(), anyString(),
                anyLong(), any()))
                .thenReturn(1);
    }

    private void stubRepairableFacts() {
        when(financeStateMapper.selectForUpdate(anyString(), any())).thenAnswer(invocation -> state);
        when(operationMapper.selectByTransaction(anyString(), any())).thenAnswer(invocation -> operation);
        when(candidateMapper.selectForUpdate("FS-1", 2)).thenAnswer(invocation -> candidate);
        when(detailMapper.selectActiveRevision("TX-1", transactionTime(), 2))
                .thenAnswer(invocation -> details);
        when(reserveMapper.selectActiveRevision("TX-1", transactionTime(), 2)).thenReturn(List.of());
        when(candidateMapper.selectDependencyReferencesForUpdate(20L)).thenReturn(List.of());
    }

    private SubmitCommand submitCommand(String requestKey, String transactionId, String operatorName) {
        return new SubmitCommand(requestKey, transactionId, transactionTime(), 7, 2, 3,
                "repair historical principal", operatorName,
                Instant.parse("2026-11-06T07:00:00Z"));
    }

    private ReviewCommand review(String repairNo, long version,
                                 ReviewDecision decision, String operatorName) {
        return new ReviewCommand(repairNo, version, decision, "reviewed", operatorName,
                Instant.parse("2026-11-06T08:00:00Z"));
    }

    private ClearingTransactionFinanceStateDO state() {
        ClearingTransactionFinanceStateDO row = new ClearingTransactionFinanceStateDO();
        row.setId(10L);
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setLabelCurrency("EUR");
        row.setClearingStatus("CLEARED");
        row.setClearingRevision(2);
        row.setSettlementStatus("NOT_SETTLED");
        row.setVersion(7);
        row.setTransactionDateTime(transactionTime());
        return row;
    }

    private ClearingTransactionOperationDO operation() {
        ClearingTransactionOperationDO row = new ClearingTransactionOperationDO();
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setMerchantOrderNo("ORDER-1");
        row.setTransactionType("PAYMENT");
        row.setTransactionStatus("SUCCESS");
        row.setLabelCurrency("EUR");
        row.setLabelAmount(new BigDecimal("100.00"));
        row.setLabelCurrencyExponent(2);
        row.setTransactionCurrency("USD");
        row.setTransactionAmount(new BigDecimal("110.00"));
        row.setCurrencyExponent(2);
        row.setTransactionDateTime(transactionTime());
        row.setTransactionUtcTime(transactionTime().minusHours(8));
        row.setTransactionTimeZone("Asia/Shanghai");
        return row;
    }

    private ClearingSettlementCandidateDO candidate() {
        ClearingSettlementCandidateDO row = new ClearingSettlementCandidateDO();
        row.setId(20L);
        row.setCandidateNo("SC-OLD");
        row.setSourceType("CLEARING_REVISION");
        row.setSourceBusinessId("FS-1");
        row.setSourceRevision(2);
        row.setSourceTransactionId("TX-1");
        row.setSourceTransactionDateTime(transactionTime());
        row.setMerchantId("M-1");
        row.setSettlementProfileId(30L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setSettlementEligibleDate(LocalDate.of(2026, 11, 7));
        row.setCandidateStatus("READY");
        row.setShadowMode(0);
        row.setVersion(3L);
        return row;
    }

    private ClearingReserveDetailDO reserve() {
        ClearingReserveDetailDO row = new ClearingReserveDetailDO();
        row.setId(30L);
        row.setReserveClearingDetailNo("RD-OLD");
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setOriginalTransactionId("TX-1");
        row.setOriginalTransactionDateTime(transactionTime());
        row.setMerchantId("M-1");
        row.setPaymentType("CARD");
        row.setPaymentMethod("VISA");
        row.setTransactionType("PAYMENT");
        row.setClearingRevision(2);
        row.setLineNo(1);
        row.setReserveActionType("HOLD");
        row.setItemCode("ROLLING_RESERVE");
        row.setItemName("Rolling reserve");
        row.setDirection("DEBIT");
        row.setReserveCurrency("EUR");
        row.setReserveCurrencyExponent(2);
        row.setBasisAmount(new BigDecimal("100.00"));
        row.setReserveRate(new BigDecimal("0.10000000"));
        row.setRetainedAmount(new BigDecimal("10.00"));
        row.setReturnedAmount(BigDecimal.ZERO);
        row.setReleasedAmount(BigDecimal.ZERO);
        row.setAdjustmentAmount(BigDecimal.ZERO);
        row.setRemainingAmount(new BigDecimal("10.00"));
        row.setFeePlanId(70L);
        row.setFeePlanVersionId(71L);
        row.setFeePlanVersionNo(3);
        row.setReserveSnapshotHash("b".repeat(64));
        row.setReserveBasis("LABEL_PRINCIPAL");
        row.setReserveDelayUnit("DAY");
        row.setReserveDelayDays(30);
        row.setRoundingMode("HALF_UP");
        row.setFormulaSnapshot("100.00 * 0.10");
        row.setRecordStatus("ACTIVE");
        row.setTransactionDateTime(transactionTime());
        row.setTransactionUtcTime(transactionTime().minusHours(8));
        row.setTransactionTimeZone("Asia/Shanghai");
        row.setCreateTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        row.setUpdateTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        return row;
    }

    private ClearingReserveStateDO reserveState(ClearingReserveDetailDO reserve) {
        ClearingReserveStateDO row = new ClearingReserveStateDO();
        row.setId(31L);
        row.setReserveStateId("RS-1");
        row.setOriginalTransactionId(reserve.getOriginalTransactionId());
        row.setOperationId(reserve.getOperationId());
        row.setOriginalFinanceStateId(reserve.getFinanceStateId());
        row.setOriginalHoldDetailNo(reserve.getReserveClearingDetailNo());
        row.setOriginalFeePlanVersionId(reserve.getFeePlanVersionId());
        row.setOriginalReserveSnapshotHash(reserve.getReserveSnapshotHash());
        row.setMerchantId(reserve.getMerchantId());
        row.setReserveCurrency(reserve.getReserveCurrency());
        row.setReserveCurrencyExponent(reserve.getReserveCurrencyExponent());
        row.setOriginalBasisAmount(reserve.getBasisAmount());
        row.setOriginalReserveRate(reserve.getReserveRate());
        row.setOriginalRoundingMode(reserve.getRoundingMode());
        row.setRetainedAmount(reserve.getRetainedAmount());
        row.setReturnedAmount(BigDecimal.ZERO);
        row.setReleasedAmount(BigDecimal.ZERO);
        row.setDebitAdjustmentAmount(BigDecimal.ZERO);
        row.setCreditAdjustmentAmount(BigDecimal.ZERO);
        row.setRemainingAmount(reserve.getRemainingAmount());
        row.setReserveStatus("OPEN");
        row.setTransactionDateTime(reserve.getOriginalTransactionDateTime());
        row.setOriginalTransactionUtcTime(reserve.getTransactionUtcTime());
        row.setTransactionTimeZone(reserve.getTransactionTimeZone());
        row.setVersion(4L);
        row.setCreateTime(reserve.getCreateTime());
        row.setUpdateTime(reserve.getUpdateTime());
        return row;
    }

    private ClearingSettlementCandidateDependencyDO dependency() {
        ClearingSettlementCandidateDependencyDO row = new ClearingSettlementCandidateDependencyDO();
        row.setId(50L);
        row.setCandidateId(40L);
        row.setDependsOnCandidateId(20L);
        row.setDependencyType("RESERVE_HOLD_POSTED");
        row.setCreateTime(LocalDateTime.of(2026, 4, 1, 10, 5));
        return row;
    }

    private ClearingSettlementCandidateDO dependentCandidate() {
        ClearingSettlementCandidateDO row = new ClearingSettlementCandidateDO();
        row.setId(40L);
        row.setCandidateNo("SC-RESERVE-RELEASE");
        row.setSourceType("RESERVE_RELEASE");
        row.setSourceBusinessId("RS-1");
        row.setSourceRevision(1);
        row.setSourceTransactionId("RRL-1");
        row.setSourceTransactionDateTime(transactionTime());
        row.setMerchantId("M-1");
        row.setCandidateStatus("READY");
        row.setVersion(6L);
        return row;
    }

    private ClearingTransactionDetailDO principal() {
        ClearingTransactionDetailDO row = detail("CD-PRINCIPAL", 1, "PRINCIPAL");
        row.setDirection("CREDIT");
        row.setLabelCurrency("EUR");
        row.setLabelAmount(new BigDecimal("100.00"));
        row.setLabelCurrencyExponent(2);
        row.setBasisCurrency("USD");
        row.setBasisAmount(new BigDecimal("110.00"));
        row.setBasisCurrencyExponent(2);
        row.setAmount(new BigDecimal("110.00"));
        row.setCurrency("USD");
        row.setCurrencyExponent(2);
        row.setFormulaSnapshot("historical transaction principal");
        return row;
    }

    private ClearingTransactionDetailDO fee() {
        ClearingTransactionDetailDO row = detail("CD-FEE", 2, "FEE");
        row.setFeeCategory("TRANSACTION_FEE");
        row.setDirection("DEBIT");
        row.setLabelCurrency("EUR");
        row.setLabelAmount(new BigDecimal("100.00"));
        row.setLabelCurrencyExponent(2);
        row.setBasisCurrency("EUR");
        row.setBasisAmount(new BigDecimal("100.00"));
        row.setBasisCurrencyExponent(2);
        row.setAmount(new BigDecimal("2.00"));
        row.setCurrency("EUR");
        row.setCurrencyExponent(2);
        row.setPercentageRate(new BigDecimal("0.02000000"));
        row.setFormulaSnapshot("100.00 * 0.02");
        row.setRuleSnapshotJson("{\"ruleId\":11}");
        row.setFeeSnapshotHash("a".repeat(64));
        return row;
    }

    private ClearingTransactionDetailDO detail(String detailNo, int lineNo, String itemType) {
        ClearingTransactionDetailDO row = new ClearingTransactionDetailDO();
        row.setId((long) lineNo);
        row.setClearingDetailNo(detailNo);
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setTransactionType("PAYMENT");
        row.setClearingRevision(2);
        row.setLineNo(lineNo);
        row.setItemType(itemType);
        row.setRecordStatus("ACTIVE");
        row.setTransactionDateTime(transactionTime());
        row.setTransactionUtcTime(transactionTime().minusHours(8));
        row.setTransactionTimeZone("Asia/Shanghai");
        row.setCreateTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        row.setUpdateTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        return row;
    }

    private LocalDateTime transactionTime() {
        return LocalDateTime.of(2026, 4, 1, 10, 0);
    }
}
