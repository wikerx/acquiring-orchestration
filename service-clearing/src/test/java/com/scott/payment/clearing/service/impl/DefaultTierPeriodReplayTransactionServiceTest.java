package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.entity.ClearingSettlementCandidateDO;
import com.scott.payment.clearing.entity.ClearingFeeTierAccumulatorDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemFactsDO;
import com.scott.payment.clearing.mapper.ClearingFeeTierAccumulatorMapper;
import com.scott.payment.clearing.mapper.ClearingSettlementCandidateMapper;
import com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReviewCommand;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReviewDecision;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证阶梯期间重放准备阶段不会覆盖已结算或保证金相关历史事实。 */
class DefaultTierPeriodReplayTransactionServiceTest {

    private final ClearingTierPeriodReplayMapper replayMapper = mock(ClearingTierPeriodReplayMapper.class);
    private final ClearingFeeTierAccumulatorMapper accumulatorMapper =
            mock(ClearingFeeTierAccumulatorMapper.class);
    private final ClearingSettlementCandidateMapper candidateMapper =
            mock(ClearingSettlementCandidateMapper.class);
    private final DefaultTierPeriodReplayTransactionService service =
            new DefaultTierPeriodReplayTransactionService(replayMapper, accumulatorMapper, candidateMapper);

    @Test
    void approveShouldRouteSettledPeriodToManualReviewWithoutChangingFacts() {
        ClearingTierPeriodReplayDO replay = replay();
        ClearingTierPeriodReplayItemFactsDO item = item("SETTLED", 0L);
        when(replayMapper.selectForUpdate("TR-1")).thenReturn(replay);
        when(replayMapper.markPreparing(eq("TR-1"), eq(0L), eq("reviewer-B"), eq("approved"), any()))
                .thenReturn(1);
        when(accumulatorMapper.selectForUpdateBatch("M-1", 11L, List.of(101L), "202608"))
                .thenReturn(List.of(accumulator()));
        when(replayMapper.selectPeriodItems(eq("M-1"), eq(11L), eq("202608"), any(), any()))
                .thenReturn(List.of(item));
        when(replayMapper.markManualReview(eq("TR-1"), eq(1L), any(), any(), any())).thenReturn(1);

        var result = service.approve(review(), List.of(101L), LocalDateTime.of(2026, 8, 26, 10, 30));

        assertThat(result.status()).isEqualTo("MANUAL_REVIEW");
        verifyNoInteractions(candidateMapper);
        verify(accumulatorMapper, org.mockito.Mockito.never()).resetPeriod(
                any(), any(), any(), any(), any());
    }

    @Test
    void approveShouldFreezeCandidatesResetAllTierRulesAndStartReplay() {
        ClearingTierPeriodReplayDO replay = replay();
        ClearingTierPeriodReplayItemFactsDO item = item("NOT_SETTLED", 0L);
        ClearingSettlementCandidateDO candidate = new ClearingSettlementCandidateDO();
        candidate.setSourceType("CLEARING_REVISION");
        candidate.setSourceBusinessId("FS-1");
        candidate.setSourceRevision(2);
        candidate.setCandidateStatus("READY");
        candidate.setVersion(0L);
        when(replayMapper.selectForUpdate("TR-1")).thenReturn(replay);
        when(replayMapper.markPreparing(eq("TR-1"), eq(0L), eq("reviewer-B"), eq("approved"), any()))
                .thenReturn(1);
        when(replayMapper.selectPeriodItems(eq("M-1"), eq(11L), eq("202608"), any(), any()))
                .thenReturn(List.of(item));
        when(accumulatorMapper.insertIfAbsentBatch(eq("M-1"), eq(11L), eq(List.of(101L)),
                eq("202608"), any())).thenReturn(1);
        when(accumulatorMapper.selectForUpdateBatch("M-1", 11L, List.of(101L), "202608"))
                .thenReturn(List.of(accumulator()));
        when(candidateMapper.selectForTierReplay(any())).thenReturn(List.of(candidate));
        when(candidateMapper.holdForTierReplay(any(), any())).thenReturn(1);
        when(replayMapper.insertItems(any())).thenReturn(1);
        when(accumulatorMapper.resetPeriod("M-1", 11L, List.of(101L), "202608",
                LocalDateTime.of(2026, 8, 26, 10, 30)))
                .thenReturn(1);
        when(replayMapper.markRunning("TR-1", 1L, 1, LocalDateTime.of(2026, 8, 26, 10, 30)))
                .thenReturn(1);

        var result = service.approve(review(), List.of(101L), LocalDateTime.of(2026, 8, 26, 10, 30));

        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.itemCount()).isEqualTo(1);
        verify(candidateMapper).holdForTierReplay(any(), eq(LocalDateTime.of(2026, 8, 26, 10, 30)));
        verify(accumulatorMapper).resetPeriod(
                "M-1", 11L, List.of(101L), "202608", LocalDateTime.of(2026, 8, 26, 10, 30));
    }

    @Test
    void approveShouldRejectSameSubmitterAndReviewerBeforeChangingState() {
        ClearingTierPeriodReplayDO replay = replay();
        when(replayMapper.selectForUpdate("TR-1")).thenReturn(replay);
        ReviewCommand sameOperator = new ReviewCommand(
                "TR-1", 0L, ReviewDecision.APPROVE, "approved", "operator-A",
                Instant.parse("2026-08-26T10:30:00Z"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.approve(
                        sameOperator, List.of(101L), LocalDateTime.of(2026, 8, 26, 10, 30)))
                .withMessageContaining("must be different");
        verify(replayMapper, never()).markPreparing(any(), anyLong(), any(), any(), any());
        verifyNoInteractions(accumulatorMapper, candidateMapper);
    }

    @Test
    void approveShouldRouteClaimedCandidateToManualReviewWithoutResettingAccumulator() {
        ClearingTierPeriodReplayDO replay = replay();
        ClearingTierPeriodReplayItemFactsDO item = item("NOT_SETTLED", 0L);
        ClearingSettlementCandidateDO candidate = new ClearingSettlementCandidateDO();
        candidate.setSourceType("CLEARING_REVISION");
        candidate.setSourceBusinessId("FS-1");
        candidate.setSourceRevision(2);
        candidate.setCandidateStatus("CLAIMED");
        candidate.setSettlementBatchNo("SB-1");
        candidate.setVersion(3L);
        when(replayMapper.selectForUpdate("TR-1")).thenReturn(replay);
        when(replayMapper.markPreparing(eq("TR-1"), eq(0L), eq("reviewer-B"), eq("approved"), any()))
                .thenReturn(1);
        when(accumulatorMapper.selectForUpdateBatch("M-1", 11L, List.of(101L), "202608"))
                .thenReturn(List.of(accumulator()));
        when(replayMapper.selectPeriodItems(eq("M-1"), eq(11L), eq("202608"), any(), any()))
                .thenReturn(List.of(item));
        when(candidateMapper.selectForTierReplay(List.of(item))).thenReturn(List.of(candidate));
        when(replayMapper.markManualReview(eq("TR-1"), eq(1L), eq("CANDIDATE_NOT_READY"), any(), any()))
                .thenReturn(1);

        var result = service.approve(review(), List.of(101L), LocalDateTime.of(2026, 8, 26, 10, 30));

        assertThat(result.status()).isEqualTo("MANUAL_REVIEW");
        verify(candidateMapper, never()).holdForTierReplay(any(), any());
        verify(accumulatorMapper, never()).resetPeriod(any(), any(), any(), any(), any());
    }

    @Test
    void approveShouldRoutePeriodWithActiveReserveFactsToManualReview() {
        ClearingTierPeriodReplayDO replay = replay();
        ClearingTierPeriodReplayItemFactsDO item = item("NOT_SETTLED", 0L);
        when(replayMapper.selectForUpdate("TR-1")).thenReturn(replay);
        when(replayMapper.markPreparing(eq("TR-1"), eq(0L), eq("reviewer-B"), eq("approved"), any()))
                .thenReturn(1);
        when(accumulatorMapper.selectForUpdateBatch("M-1", 11L, List.of(101L), "202608"))
                .thenReturn(List.of(accumulator()));
        when(replayMapper.selectPeriodItems(eq("M-1"), eq(11L), eq("202608"), any(), any()))
                .thenReturn(List.of(item));
        when(replayMapper.countActiveReserveDetails(eq("M-1"), eq(11L), any(), any()))
                .thenReturn(1L);
        when(replayMapper.markManualReview(eq("TR-1"), eq(1L), eq("RESERVE_FACTS_PRESENT"),
                any(), any())).thenReturn(1);

        var result = service.approve(review(), List.of(101L),
                LocalDateTime.of(2026, 8, 26, 10, 30));

        assertThat(result.status()).isEqualTo("MANUAL_REVIEW");
        verifyNoInteractions(candidateMapper);
        verify(accumulatorMapper, never()).resetPeriod(any(), any(), any(), any(), any());
    }

    @Test
    void eighthItemFailureShouldMoveReplayToManualReview() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 30);
        ClearingTierPeriodReplayDO replay = replay();
        replay.setReplayStatus("RUNNING");
        replay.setVersion(5L);
        replay.setItemCount(10);
        replay.setCompletedCount(2);
        ClearingTierPeriodReplayItemDO item = new ClearingTierPeriodReplayItemDO();
        item.setReplayNo("TR-1");
        item.setSequenceNo(3);
        item.setTransactionId("PAY-3");
        item.setAttemptCount(7);
        item.setVersion(4L);
        when(replayMapper.selectForUpdate("TR-1")).thenReturn(replay);
        when(replayMapper.selectNextItemForUpdate("TR-1", now)).thenReturn(item);
        when(replayMapper.markItemFailed(eq("TR-1"), eq(3), eq(4L), any(),
                eq("TIER_REPLAY_ITEM_FAILED"), eq("temporary failure"), eq(now))).thenReturn(1);
        when(replayMapper.recordItemFailure("TR-1", 5L, "MANUAL_REVIEW",
                "TIER_REPLAY_ITEM_FAILED", "temporary failure", now)).thenReturn(1);

        var result = service.recordFailure(
                "TR-1", item, "TIER_REPLAY_ITEM_FAILED", "temporary failure", now);

        assertThat(result.status()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.version()).isEqualTo(6L);
        verify(replayMapper).recordItemFailure(
                "TR-1", 5L, "MANUAL_REVIEW", "TIER_REPLAY_ITEM_FAILED", "temporary failure", now);
    }

    private ReviewCommand review() {
        return new ReviewCommand("TR-1", 0L, ReviewDecision.APPROVE, "approved", "reviewer-B",
                Instant.parse("2026-08-26T10:30:00Z"));
    }

    private ClearingTierPeriodReplayDO replay() {
        ClearingTierPeriodReplayDO row = new ClearingTierPeriodReplayDO();
        row.setReplayNo("TR-1");
        row.setMerchantId("M-1");
        row.setFeePlanId(10L);
        row.setFeePlanVersionId(11L);
        row.setTriggerFeeRuleId(101L);
        row.setPeriodKey("202608");
        row.setPeriodStart(LocalDateTime.of(2026, 8, 1, 0, 0));
        row.setPeriodEnd(LocalDateTime.of(2026, 9, 1, 0, 0));
        row.setSubmitOperator("operator-A");
        row.setReplayStatus("PENDING_REVIEW");
        row.setVersion(0L);
        return row;
    }

    private ClearingTierPeriodReplayItemFactsDO item(String settlementStatus, long reserveDetailCount) {
        ClearingTierPeriodReplayItemFactsDO row = new ClearingTierPeriodReplayItemFactsDO();
        row.setFinanceStateId("FS-1");
        row.setTransactionId("PAY-1");
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 2, 10, 0));
        row.setClearingRevision(2);
        row.setFinanceStateVersion(4);
        row.setClearingCompleteTime(LocalDateTime.of(2026, 8, 2, 10, 0, 1));
        row.setSettlementStatus(settlementStatus);
        row.setReserveDetailCount(reserveDetailCount);
        return row;
    }

    private ClearingFeeTierAccumulatorDO accumulator() {
        ClearingFeeTierAccumulatorDO row = new ClearingFeeTierAccumulatorDO();
        row.setFeeRuleId(101L);
        row.setVersion(0L);
        return row;
    }
}
