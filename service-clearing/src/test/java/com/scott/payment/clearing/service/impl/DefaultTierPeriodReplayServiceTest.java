package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO;
import com.scott.payment.clearing.entity.ClearingTransactionMerchantSnapshotDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionMerchantSnapshotMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingCompletionService;
import com.scott.payment.clearing.service.ClearingPreparationService;
import com.scott.payment.clearing.service.FeeConfigurationSnapshotService;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReplayResult;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReviewCommand;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReviewDecision;
import com.scott.payment.clearing.service.TierPeriodReplayService.SubmitCommand;
import com.scott.payment.clearing.service.TierPeriodReplayTransactionService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证阶梯期间重放编排只使用冻结版本、稳定月份边界和逐项短事务。 */
class DefaultTierPeriodReplayServiceTest {

    private final ClearingTierPeriodReplayMapper replayMapper = mock(ClearingTierPeriodReplayMapper.class);
    private final TierPeriodReplayTransactionService transactionService =
            mock(TierPeriodReplayTransactionService.class);
    private final ClearingTransactionOperationMapper operationMapper =
            mock(ClearingTransactionOperationMapper.class);
    private final ClearingTransactionMerchantSnapshotMapper merchantSnapshotMapper =
            mock(ClearingTransactionMerchantSnapshotMapper.class);
    private final FeeConfigurationSnapshotService snapshotService =
            mock(FeeConfigurationSnapshotService.class);
    private final ClearingPreparationService preparationService = mock(ClearingPreparationService.class);
    private final ClearingCompletionService completionService = mock(ClearingCompletionService.class);
    private final ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
    private final DefaultTierPeriodReplayService service = new DefaultTierPeriodReplayService(
            replayMapper, transactionService, operationMapper, merchantSnapshotMapper,
            snapshotService, preparationService, completionService, metrics);

    @Test
    void submitShouldUseRequestKeyIdempotencyAndExactUtcMonthBoundaries() {
        AtomicReference<ClearingTierPeriodReplayDO> persisted = new AtomicReference<>();
        when(replayMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            persisted.compareAndSet(null, invocation.getArgument(0));
            return persisted.get() == invocation.<ClearingTierPeriodReplayDO>getArgument(0) ? 1 : 0;
        });
        when(replayMapper.selectByRequestKeyForUpdate("REQ-1")).thenAnswer(invocation -> persisted.get());
        SubmitCommand command = new SubmitCommand(
                " REQ-1 ", " M-1 ", 10L, 11L, 101L, "202608", " correct tier ",
                "admin-account:88/Operator", Instant.parse("2026-08-26T10:00:00Z"));

        ReplayResult first = service.submit(command);
        ReplayResult duplicate = service.submit(command);

        ArgumentCaptor<ClearingTierPeriodReplayDO> rowCaptor =
                ArgumentCaptor.forClass(ClearingTierPeriodReplayDO.class);
        verify(replayMapper, org.mockito.Mockito.times(2)).insertIdempotent(rowCaptor.capture());
        ClearingTierPeriodReplayDO row = rowCaptor.getAllValues().get(0);
        assertThat(row.getReplayNo()).startsWith("TRP").hasSize(35);
        assertThat(row.getPeriodStart()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(row.getPeriodEnd()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(row.getReason()).isEqualTo("correct tier");
        assertThat(duplicate.replayNo()).isEqualTo(first.replayNo());
        assertThat(duplicate.status()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void submitShouldRejectInvalidCalendarMonthBeforePersistence() {
        SubmitCommand command = new SubmitCommand(
                "REQ-1", "M-1", 10L, 11L, 101L, "202613", "correct tier",
                "admin-account:88/Operator", Instant.parse("2026-08-26T10:00:00Z"));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> service.submit(command))
                .withMessageContaining("yyyyMM");
        verify(replayMapper, never()).insertIdempotent(any());
    }

    @Test
    void reviewShouldRequireTriggerRuleInsideImmutableTierClosure() {
        ClearingTierPeriodReplayDO replay = replay();
        replay.setTriggerFeeRuleId(101L);
        FeeVersionSnapshot snapshot = snapshot(202L);
        when(replayMapper.selectByReplayNo("TR-1")).thenReturn(replay);
        when(snapshotService.loadForRecalculation("M-1", 10L, 11L, replay.getPeriodStart()))
                .thenReturn(snapshot);
        ReviewCommand command = new ReviewCommand(
                "TR-1", 0L, ReviewDecision.APPROVE, "approved", "reviewer-B",
                Instant.parse("2026-08-26T10:30:00Z"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(command))
                .withMessageContaining("immutable tier rule closure");
        verify(transactionService, never()).approve(any(), any(), any());
    }

    @Test
    void runDueShouldRecalculateNextItemWithFrozenVersionAndStableReplayIdentity() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 30);
        ClearingTierPeriodReplayDO replay = replay();
        replay.setReplayStatus("RUNNING");
        replay.setItemCount(1);
        replay.setCompletedCount(0);
        ClearingTierPeriodReplayItemDO item = item(now);
        ClearingTransactionOperationDO operation = operation(item);
        ClearingTransactionMerchantSnapshotDO merchantSnapshot = merchantSnapshot(item, operation);
        FeeVersionSnapshot snapshot = snapshot(101L);
        CompletionCommand completionCommand = mock(CompletionCommand.class);
        when(replayMapper.selectRunnable(now, 20)).thenReturn(List.of(replay));
        when(replayMapper.selectNextItem("TR-1", now)).thenReturn(item);
        when(operationMapper.selectByTransaction("PAY-1", item.getTransactionDateTime())).thenReturn(operation);
        when(merchantSnapshotMapper.selectByTransaction("PAY-1", item.getTransactionDateTime()))
                .thenReturn(merchantSnapshot);
        when(snapshotService.loadForRecalculation("M-1", 10L, 11L,
                merchantSnapshot.getFeeSnapshotTime())).thenReturn(snapshot);
        when(preparationService.prepareForRecalculation(any(), any(), eq("tier-replay:TR-1"), eq(snapshot)))
                .thenReturn(completionCommand);

        int processed = service.runDue(20, Instant.parse("2026-08-26T10:30:00Z"));

        assertThat(processed).isEqualTo(1);
        verify(completionService).recalculateTierPeriod(
                completionCommand, "TR-1", 1, 4, 2, now);
        verify(metrics).recordTierReplay("COMPLETED");
    }

    private ClearingTierPeriodReplayDO replay() {
        ClearingTierPeriodReplayDO row = new ClearingTierPeriodReplayDO();
        row.setReplayNo("TR-1");
        row.setRequestKey("REQ-1");
        row.setMerchantId("M-1");
        row.setFeePlanId(10L);
        row.setFeePlanVersionId(11L);
        row.setTriggerFeeRuleId(101L);
        row.setPeriodKey("202608");
        row.setPeriodStart(LocalDateTime.of(2026, 8, 1, 0, 0));
        row.setPeriodEnd(LocalDateTime.of(2026, 9, 1, 0, 0));
        row.setReplayStatus("PENDING_REVIEW");
        row.setVersion(0L);
        return row;
    }

    private ClearingTierPeriodReplayItemDO item(LocalDateTime now) {
        ClearingTierPeriodReplayItemDO row = new ClearingTierPeriodReplayItemDO();
        row.setReplayNo("TR-1");
        row.setSequenceNo(1);
        row.setFinanceStateId("FS-1");
        row.setTransactionId("PAY-1");
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 2, 10, 0));
        row.setExpectedClearingRevision(2);
        row.setExpectedFinanceStateVersion(4);
        row.setClearingCompleteTime(now.minusDays(1));
        row.setItemStatus("PENDING");
        row.setAttemptCount(0);
        row.setVersion(0L);
        return row;
    }

    private ClearingTransactionOperationDO operation(ClearingTierPeriodReplayItemDO item) {
        ClearingTransactionOperationDO row = new ClearingTransactionOperationDO();
        row.setTransactionId(item.getTransactionId());
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setMerchantOrderNo("ORDER-1");
        row.setTransactionType("PAYMENT");
        row.setTransactionStatus("SUCCESS");
        row.setLabelCurrency("USD");
        row.setLabelAmount(new BigDecimal("100.00"));
        row.setApprovedCurrency("USD");
        row.setApprovedAmount(new BigDecimal("100.00"));
        row.setTransactionCurrency("USD");
        row.setTransactionAmount(new BigDecimal("100.00"));
        row.setCurrencyExponent(2);
        row.setTransactionDateTime(item.getTransactionDateTime());
        row.setTransactionUtcTime(item.getTransactionDateTime());
        row.setTransactionTimeZone("UTC");
        row.setVersion(3);
        return row;
    }

    private ClearingTransactionMerchantSnapshotDO merchantSnapshot(
            ClearingTierPeriodReplayItemDO item, ClearingTransactionOperationDO operation) {
        ClearingTransactionMerchantSnapshotDO row = new ClearingTransactionMerchantSnapshotDO();
        row.setTransactionId(item.getTransactionId());
        row.setOperationId(operation.getOperationId());
        row.setMerchantId("M-1");
        row.setFeeSnapshotTime(LocalDateTime.of(2026, 8, 2, 9, 59));
        return row;
    }

    private FeeVersionSnapshot snapshot(long ruleId) {
        FeeRuleConfigurationSnapshot rule = mock(FeeRuleConfigurationSnapshot.class);
        FeeRuleSnapshot calculation = mock(FeeRuleSnapshot.class);
        when(rule.ruleId()).thenReturn(ruleId);
        when(rule.calculationRule()).thenReturn(calculation);
        when(calculation.feeMode()).thenReturn(FeeMode.TIER);
        FeeVersionSnapshot snapshot = mock(FeeVersionSnapshot.class);
        when(snapshot.rules()).thenReturn(List.of(rule));
        return snapshot;
    }
}
