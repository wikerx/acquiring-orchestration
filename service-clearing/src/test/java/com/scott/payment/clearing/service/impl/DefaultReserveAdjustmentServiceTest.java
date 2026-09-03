package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.entity.ClearingMerchantSettlementProfileDO;
import com.scott.payment.clearing.entity.ClearingReserveAdjustmentDO;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.mapper.ClearingMerchantSettlementProfileMapper;
import com.scott.payment.clearing.mapper.ClearingReserveAdjustmentMapper;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import com.scott.payment.clearing.service.ReserveAdjustmentService.ReviewCommand;
import com.scott.payment.clearing.service.ReserveAdjustmentService.ReviewDecision;
import com.scott.payment.clearing.service.ReserveAdjustmentService.SubmitCommand;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.finance.reserve.core.ReserveCalculator;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentDirection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultReserveAdjustmentServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证保证金调整申请、双人复核和独立结算事实的同事务边界。
 * @status : create
 */
class DefaultReserveAdjustmentServiceTest {

    private final ClearingReserveAdjustmentMapper adjustmentMapper =
            mock(ClearingReserveAdjustmentMapper.class);
    private final ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
    private final ClearingMerchantSettlementProfileMapper profileMapper =
            mock(ClearingMerchantSettlementProfileMapper.class);
    private final ClearingSettlementCandidateService candidateService =
            mock(ClearingSettlementCandidateService.class);
    private final ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
    private final DefaultReserveAdjustmentService service = new DefaultReserveAdjustmentService(
            adjustmentMapper, reserveMapper, profileMapper, candidateService,
            new ReserveCalculator(), metrics);

    @Test
    void submitShouldFreezeLabelCurrencyIdentityAndExpectedStateVersion() {
        ClearingReserveStateDO state = state();
        AtomicReference<ClearingReserveAdjustmentDO> persisted = new AtomicReference<>();
        when(reserveMapper.selectStateForUpdate("PAY-1", state.getTransactionDateTime())).thenReturn(state);
        when(adjustmentMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });
        when(adjustmentMapper.selectByRequestKeyForUpdate("REQ-1"))
                .thenAnswer(invocation -> persisted.get());

        var result = service.submit(new SubmitCommand(
                "REQ-1", "RS-1", "PAY-1", state.getTransactionDateTime(), 3L,
                ReserveAdjustmentDirection.DEBIT, new BigDecimal("2.00"),
                LocalDate.of(2026, 12, 1), "correct reserve shortfall", "operator-A",
                Instant.parse("2026-08-26T10:30:00Z")));

        assertThat(result.status()).isEqualTo("PENDING_REVIEW");
        assertThat(result.adjustmentNo()).startsWith("RA");
        assertThat(persisted.get().getReserveCurrency()).isEqualTo("EUR");
        assertThat(persisted.get().getReserveCurrencyExponent()).isEqualTo(2);
        assertThat(persisted.get().getExpectedReserveStateVersion()).isEqualTo(3L);
        assertThat(persisted.get().getAdjustmentAmount()).isEqualByComparingTo("2.00");
        verify(metrics).recordReserveAdjustment("SUBMITTED");
    }

    @Test
    void submitSuccessMetricShouldWaitForTransactionCommit() {
        ClearingReserveStateDO state = state();
        AtomicReference<ClearingReserveAdjustmentDO> persisted = new AtomicReference<>();
        when(reserveMapper.selectStateForUpdate("PAY-1", state.getTransactionDateTime())).thenReturn(state);
        when(adjustmentMapper.insertIdempotent(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });
        when(adjustmentMapper.selectByRequestKeyForUpdate("REQ-1"))
                .thenAnswer(invocation -> persisted.get());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit(new SubmitCommand(
                    "REQ-1", "RS-1", "PAY-1", state.getTransactionDateTime(), 3L,
                    ReserveAdjustmentDirection.DEBIT, new BigDecimal("2.00"),
                    LocalDate.of(2026, 12, 1), "correct reserve shortfall", "operator-A",
                    Instant.parse("2026-08-26T10:30:00Z")));

            verify(metrics, org.mockito.Mockito.never()).recordReserveAdjustment("SUBMITTED");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(metrics).recordReserveAdjustment("SUBMITTED");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void approveShouldAppendDebitAdjustmentUpdateStateAndCreateCandidate() {
        ClearingReserveStateDO state = state();
        ClearingReserveAdjustmentDO request = request();
        when(adjustmentMapper.selectForUpdate("RA-1")).thenReturn(request);
        when(reserveMapper.selectStateForUpdate("PAY-1", state.getTransactionDateTime())).thenReturn(state);
        when(reserveMapper.selectHoldDetail("HOLD-1", state.getTransactionDateTime())).thenReturn(hold());
        when(profileMapper.selectActiveProfile("M-1", LocalDate.of(2026, 8, 26))).thenReturn(profile());
        when(reserveMapper.insertDetail(any())).thenReturn(1);
        when(reserveMapper.applyAdjustment(eq("PAY-1"), eq(state.getTransactionDateTime()), eq(3L),
                eq("DEBIT"), eq(new BigDecimal("2.00")), eq(new BigDecimal("10.00")),
                eq(LocalDate.of(2026, 12, 1)), any())).thenReturn(1);
        when(adjustmentMapper.markExecuted(eq("RA-1"), eq(0L), eq("operator-B"),
                eq("approved"), any(), any(), eq(4), any())).thenReturn(1);

        var result = service.review(new ReviewCommand(
                "RA-1", 0L, ReviewDecision.APPROVE, "approved", "operator-B",
                Instant.parse("2026-08-26T10:30:00Z")));

        assertThat(result.status()).isEqualTo("EXECUTED");
        assertThat(result.sourceRevision()).isEqualTo(4);
        ArgumentCaptor<ClearingReserveDetailDO> detailCaptor =
                ArgumentCaptor.forClass(ClearingReserveDetailDO.class);
        verify(reserveMapper).insertDetail(detailCaptor.capture());
        ClearingReserveDetailDO detail = detailCaptor.getValue();
        assertThat(detail.getReserveActionType()).isEqualTo("ADJUSTMENT");
        assertThat(detail.getDirection()).isEqualTo("DEBIT");
        assertThat(detail.getReserveCurrency()).isEqualTo("EUR");
        assertThat(detail.getAdjustmentAmount()).isEqualByComparingTo("2.00");
        assertThat(detail.getRemainingAmount()).isEqualByComparingTo("10.00");
        verify(candidateService).createAdjustment(
                eq("RA-1"), eq(4), eq(result.transactionId()),
                eq(LocalDateTime.of(2026, 8, 26, 18, 30)), eq("M-1"), eq("USD"),
                eq(LocalDate.of(2026, 8, 26)), eq(LocalDateTime.of(2026, 8, 26, 10, 30)));
        verify(metrics).recordReserveAdjustment("APPROVED");
    }

    @Test
    void reviewShouldRejectSameOperatorBeforeAnyFinancialWrite() {
        ClearingReserveAdjustmentDO request = request();
        when(adjustmentMapper.selectForUpdate("RA-1")).thenReturn(request);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.review(new ReviewCommand(
                        "RA-1", 0L, ReviewDecision.APPROVE, "approved", "operator-A",
                        Instant.parse("2026-08-26T10:30:00Z"))))
                .withMessageContaining("different");

        verifyNoInteractions(profileMapper, candidateService);
        verify(metrics).recordReserveAdjustment("FAILED");
    }

    private ClearingReserveStateDO state() {
        ClearingReserveStateDO row = new ClearingReserveStateDO();
        row.setReserveStateId("RS-1");
        row.setOriginalTransactionId("PAY-1");
        row.setOperationId("OP-1");
        row.setOriginalFinanceStateId("FS-1");
        row.setOriginalHoldDetailNo("HOLD-1");
        row.setOriginalFeePlanVersionId(11L);
        row.setOriginalReserveSnapshotHash("a".repeat(64));
        row.setMerchantId("M-1");
        row.setReserveCurrency("EUR");
        row.setReserveCurrencyExponent(2);
        row.setOriginalBasisAmount(new BigDecimal("100.00"));
        row.setOriginalReserveRate(new BigDecimal("10"));
        row.setOriginalRoundingMode("HALF_UP");
        row.setRetainedAmount(new BigDecimal("10.00"));
        row.setReturnedAmount(new BigDecimal("2.00"));
        row.setReleasedAmount(BigDecimal.ZERO.setScale(2));
        row.setDebitAdjustmentAmount(BigDecimal.ZERO.setScale(2));
        row.setCreditAdjustmentAmount(BigDecimal.ZERO.setScale(2));
        row.setRemainingAmount(new BigDecimal("8.00"));
        row.setExpectedReserveReleaseDate(LocalDate.of(2026, 10, 1));
        row.setReserveStatus("OPEN");
        row.setTransactionDateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        row.setOriginalTransactionUtcTime(LocalDateTime.of(2026, 1, 1, 2, 0));
        row.setTransactionTimeZone("Asia/Shanghai");
        row.setVersion(3L);
        return row;
    }

    private ClearingReserveAdjustmentDO request() {
        ClearingReserveAdjustmentDO row = new ClearingReserveAdjustmentDO();
        row.setAdjustmentNo("RA-1");
        row.setRequestKey("REQ-1");
        row.setReserveStateId("RS-1");
        row.setOriginalTransactionId("PAY-1");
        row.setOriginalTransactionDateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        row.setMerchantId("M-1");
        row.setReserveCurrency("EUR");
        row.setReserveCurrencyExponent(2);
        row.setDirection("DEBIT");
        row.setAdjustmentAmount(new BigDecimal("2.00"));
        row.setRequestedReleaseDate(LocalDate.of(2026, 12, 1));
        row.setExpectedReserveStateVersion(3L);
        row.setReason("correct reserve shortfall");
        row.setSubmitOperator("operator-A");
        row.setAdjustmentStatus("PENDING_REVIEW");
        row.setVersion(0L);
        return row;
    }

    private ClearingReserveDetailDO hold() {
        ClearingReserveDetailDO row = new ClearingReserveDetailDO();
        row.setReserveClearingDetailNo("HOLD-1");
        row.setReserveActionType("HOLD");
        row.setReserveCurrency("EUR");
        row.setReserveCurrencyExponent(2);
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setFeePlanId(10L);
        row.setFeePlanVersionId(11L);
        row.setFeePlanVersionNo(2);
        row.setReserveSnapshotHash("a".repeat(64));
        row.setReserveBasis("LABEL_AMOUNT");
        row.setReserveDelayUnit("D");
        row.setReserveDelayDays(180);
        row.setRoundingMode("HALF_UP");
        return row;
    }

    private ClearingMerchantSettlementProfileDO profile() {
        ClearingMerchantSettlementProfileDO row = new ClearingMerchantSettlementProfileDO();
        row.setId(100L);
        row.setMerchantId("M-1");
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        return row;
    }
}
