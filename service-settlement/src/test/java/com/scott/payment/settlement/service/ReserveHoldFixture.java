package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.entity.*;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 模拟先生成释放、后修复本金并资金化新 HOLD 的冻结事实。 */
final class ReserveHoldFixture {
    static final LocalDateTime ORIGINAL_TIME = LocalDateTime.of(2026, 10, 31, 10, 0);
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);
    final SettlementBatchDO batch = new SettlementBatchDO();
    final SettlementCandidateDO candidate = new SettlementCandidateDO();
    final SettlementReserveClearingDetailDO release = new SettlementReserveClearingDetailDO();
    final SettlementReserveHoldMigrationDO proof = new SettlementReserveHoldMigrationDO();
    final SettlementReserveHoldSnapshotDO oldHold = new SettlementReserveHoldSnapshotDO();
    final SettlementReserveHoldSnapshotDO newHold = new SettlementReserveHoldSnapshotDO();
    final SettlementReserveHoldStateDO state = new SettlementReserveHoldStateDO();
    final MerchantReserveItemDO item = new MerchantReserveItemDO();

    ReserveHoldFixture() {
        batch.setSettlementBatchNo("SB20261120-00000001");batch.setMerchantId("TEST-MERCHANT");
        batch.setSettlementAccountId(1L);batch.setSettlementProfileId(7L);batch.setTargetCurrency("USD");
        batch.setTargetCurrencyExponent(2);batch.setCandidateCount(1);batch.setBatchType("RESERVE_RELEASE");
        batch.setBusinessDate(LocalDate.of(2026, 11, 20));batch.setBatchStatus("CALCULATED");
        batch.setDailySequence(1);batch.setCutoffBeginTime(NOW.minusDays(2));batch.setCutoffEndTime(NOW.minusDays(1));
        batch.setBusinessTimeZone("Asia/Shanghai");batch.setVersion(7L);batch.setCreateMode("AUTO");batch.setProcessingOwner("test-worker");
        batch.setProcessingDeadline(NOW.plusMinutes(10));
        candidate.setId(10L);candidate.setCandidateNo("SC-RELEASE");candidate.setSourceType("RESERVE_RELEASE");
        candidate.setSourceBusinessId("RESERVE-STATE");candidate.setSourceRevision(1);
        candidate.setSourceTransactionId("RELEASE-TX");candidate.setSourceTransactionDateTime(NOW.minusDays(18));
        candidate.setMerchantId(batch.getMerchantId());candidate.setSettlementProfileId(7L);
        candidate.setTargetCurrency("USD");candidate.setTargetCurrencyExponent(2);
        candidate.setCandidateStatus("CLAIMED");candidate.setSettlementBatchNo(batch.getSettlementBatchNo());
        candidate.setVersion(4L);candidate.setShadowMode(0);
        release.setId(30L);release.setReserveClearingDetailNo("RELEASE-DETAIL");
        release.setSourceReserveDetailNo("OLD-HOLD");release.setFinanceStateId("RESERVE-STATE");
        release.setTransactionId("RELEASE-TX");release.setTransactionDateTime(candidate.getSourceTransactionDateTime());
        release.setOriginalTransactionId("PAYMENT-TX");release.setOriginalTransactionDateTime(ORIGINAL_TIME);
        release.setOperationId("PAYMENT-OP");release.setMerchantId(batch.getMerchantId());
        release.setReserveActionType("RELEASE");release.setDirection("CREDIT");release.setClearingRevision(1);
        release.setLineNo(1);release.setReserveCurrency("USD");release.setReserveCurrencyExponent(2);
        release.setReleasedAmount(new BigDecimal("5.26"));release.setRecordStatus("ACTIVE");
        release.setPaymentType("CARD");release.setPaymentMethod("CARD");release.setTransactionType("RESERVE_RELEASE");
        release.setRoundingMode("HALF_UP");
        oldHold.setLineNo(1);oldHold.setId(20L);oldHold.setReserveClearingDetailNo("OLD-HOLD");oldHold.setFinanceStateId("PAYMENT-STATE");
        oldHold.setTransactionId("PAYMENT-TX");oldHold.setTransactionDateTime(ORIGINAL_TIME);
        oldHold.setOriginalTransactionId("PAYMENT-TX");oldHold.setOriginalTransactionDateTime(ORIGINAL_TIME);
        oldHold.setOperationId("PAYMENT-OP");oldHold.setMerchantId(batch.getMerchantId());
        oldHold.setPaymentType("CARD");oldHold.setPaymentMethod("CARD");oldHold.setTransactionType("CAPTURE");
        oldHold.setReserveActionType("HOLD");oldHold.setDirection("DEBIT");oldHold.setClearingRevision(1);
        oldHold.setReserveCurrency("USD");oldHold.setReserveCurrencyExponent(2);
        oldHold.setRetainedAmount(new BigDecimal("5.26"));oldHold.setRemainingAmount(new BigDecimal("5.26"));
        oldHold.setReturnedAmount(BigDecimal.ZERO);oldHold.setReleasedAmount(BigDecimal.ZERO);
        oldHold.setAdjustmentAmount(BigDecimal.ZERO);oldHold.setBasisAmount(new BigDecimal("105.20"));
        oldHold.setReserveRate(new BigDecimal("0.05"));oldHold.setFeePlanId(1L);oldHold.setFeePlanVersionId(2L);
        oldHold.setFeePlanVersionNo(1);oldHold.setReserveSnapshotHash("frozen-reserve-rule");
        oldHold.setReserveBasis("LABEL_AMOUNT");oldHold.setReserveDelayUnit("D");oldHold.setReserveDelayDays(2);
        oldHold.setRoundingMode("HALF_UP");oldHold.setFormulaSnapshot("label amount * frozen rate");
        oldHold.setExpectedReserveReleaseDate(LocalDate.of(2026,11,2));oldHold.setRecordStatus("SUPERSEDED");
        BeanUtils.copyProperties(oldHold,newHold);newHold.setId(21L);newHold.setReserveClearingDetailNo("NEW-HOLD");
        newHold.setClearingRevision(2);newHold.setRecordStatus("ACTIVE");
        proof.setRepairNo("REPAIR-1");proof.setOldHoldDetailNo("OLD-HOLD");proof.setNewHoldDetailNo("NEW-HOLD");
        proof.setReserveStateId("RESERVE-STATE");proof.setOldStateVersion(1L);proof.setNewStateVersion(2L);
        proof.setRepairStatus("EXECUTED");proof.setMerchantId(batch.getMerchantId());
        proof.setTransactionId("PAYMENT-TX");proof.setTransactionDateTime(ORIGINAL_TIME);
        proof.setFinanceStateId("PAYMENT-STATE");proof.setOldRevision(1);proof.setNewRevision(2);
        proof.setReserveFactCount(1);proof.setReserveStateCount(1);proof.setDependencyCount(1);
        proof.setOldCandidateId(11L);proof.setNewCandidateId(12L);proof.setDependentCandidateId(10L);
        proof.setOldDependsOnCandidateId(11L);proof.setNewDependsOnCandidateId(12L);
        proof.setDependencyType("RESERVE_HOLD_POSTED");proof.setCurrentDependentCandidateId(10L);
        proof.setCurrentDependsOnCandidateId(12L);proof.setCurrentDependencyType("RESERVE_HOLD_POSTED");
        proof.setNewCandidateStatus("POSTED");proof.setNewCandidateSourceType("CLEARING_REVISION");
        proof.setNewCandidateSourceBusinessId("PAYMENT-STATE");proof.setNewCandidateTransactionId("PAYMENT-TX");
        proof.setNewCandidateTransactionDateTime(ORIGINAL_TIME);proof.setNewCandidateRevision(2);
        proof.setNewCandidateMerchantId(batch.getMerchantId());proof.setNewCandidateProfileId(7L);
        proof.setNewCandidateTargetCurrency("USD");proof.setNewCandidateTargetCurrencyExponent(2);
        state.setReserveStateId("RESERVE-STATE");state.setOriginalTransactionId("PAYMENT-TX");
        state.setTransactionDateTime(ORIGINAL_TIME);state.setOriginalFinanceStateId("PAYMENT-STATE");
        state.setOriginalHoldDetailNo("NEW-HOLD");state.setMerchantId(batch.getMerchantId());
        state.setReserveCurrency("USD");state.setReserveCurrencyExponent(2);state.setReserveStatus("FULLY_RELEASED");
        state.setRetainedAmount(new BigDecimal("5.26"));state.setReleasedAmount(new BigDecimal("5.26"));
        state.setRemainingAmount(BigDecimal.ZERO);state.setReturnedAmount(BigDecimal.ZERO);
        state.setDebitAdjustmentAmount(BigDecimal.ZERO);state.setCreditAdjustmentAmount(BigDecimal.ZERO);state.setVersion(2L);
        item.setId(40L);item.setReserveNo("RS-NEW-HOLD");item.setSourceBusinessNo("NEW-HOLD");
        item.setSourceTransactionId("PAYMENT-TX");item.setMerchantId(batch.getMerchantId());item.setAccountId(1L);
        item.setCurrency("USD");item.setRetainedAmount(new BigDecimal("5.26"));item.setReturnedAmount(BigDecimal.ZERO);
        item.setReleasedAmount(BigDecimal.ZERO);item.setDebitAdjustmentAmount(BigDecimal.ZERO);
        item.setCreditAdjustmentAmount(BigDecimal.ZERO);item.setReversedAmount(BigDecimal.ZERO);
        item.setReserveStatus("HELD");item.setVersion(0L);
    }
    SettlementBatchFacts facts() {return new SettlementBatchFacts(List.of(candidate),List.of(),List.of(release),Set.of());}
}
