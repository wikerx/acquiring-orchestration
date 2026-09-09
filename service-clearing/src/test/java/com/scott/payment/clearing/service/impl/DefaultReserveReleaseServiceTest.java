package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.entity.ClearingMerchantSettlementProfileDO;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.mapper.ClearingMerchantSettlementProfileMapper;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import com.scott.payment.clearing.service.ReserveReleaseService.ReserveReleaseOutcome;
import com.scott.payment.finance.reserve.core.ReserveCalculator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultReserveReleaseServiceTest
 * @date : 2026-08-26 18:35
 * @email : scott_x@163.com
 * @description : 验证到期保证金在单条主库事务中追加RELEASE明细、清零剩余负债并生成独立结算候选。
 * @status : create
 */
class DefaultReserveReleaseServiceTest {

    private final ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
    private final ClearingMerchantSettlementProfileMapper profileMapper =
            mock(ClearingMerchantSettlementProfileMapper.class);
    private final ClearingSettlementCandidateService candidateService =
            mock(ClearingSettlementCandidateService.class);
    private final DefaultReserveReleaseService service = new DefaultReserveReleaseService(
            reserveMapper, profileMapper, candidateService, new ReserveCalculator());

    /** 到期释放必须使用当前剩余标签币种金额，并形成可独立结算的稳定动作。 */
    @Test
    void releaseShouldAppendLabelCurrencyFactAndCreateReserveReleaseCandidate() {
        System.out.println("保证金到期释放：验证8 EUR剩余负债生成独立RELEASE明细和候选");
        LocalDateTime originalTime = LocalDateTime.of(2026, 1, 1, 10, 0);
        ClearingReserveStateDO state = state(originalTime);
        ClearingReserveDetailDO hold = hold(originalTime);
        when(reserveMapper.selectStateForUpdate("PAY-1", originalTime)).thenReturn(state);
        when(reserveMapper.selectHoldDetail("HOLD-1", originalTime)).thenReturn(hold);
        when(profileMapper.selectActiveProfile("M-1", LocalDate.of(2026, 8, 26)))
                .thenReturn(profile());
        when(reserveMapper.insertDetail(any())).thenReturn(1);
        when(reserveMapper.applyRelease(eq("PAY-1"), eq(originalTime), eq(3L),
                eq(new BigDecimal("8.00")), any(), any())).thenReturn(1);

        var result = service.release("RS-1", "PAY-1", originalTime,
                Instant.parse("2026-08-26T10:30:00Z"));

        assertThat(result.outcome()).isEqualTo(ReserveReleaseOutcome.RELEASED);
        assertThat(result.releaseTransactionId()).startsWith("RRL");
        assertThat(result.sourceRevision()).isEqualTo(4);
        ArgumentCaptor<ClearingReserveDetailDO> detailCaptor =
                ArgumentCaptor.forClass(ClearingReserveDetailDO.class);
        verify(reserveMapper).insertDetail(detailCaptor.capture());
        ClearingReserveDetailDO detail = detailCaptor.getValue();
        assertThat(detail.getReserveActionType()).isEqualTo("RELEASE");
        assertThat(detail.getDirection()).isEqualTo("CREDIT");
        assertThat(detail.getReserveCurrency()).isEqualTo("EUR");
        assertThat(detail.getReleasedAmount()).isEqualByComparingTo("8.00");
        assertThat(detail.getRemainingAmount()).isZero();
        assertThat(detail.getTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 26, 18, 30));
        verify(candidateService).createReserveRelease(
                eq("RS-1"), eq(4), eq(result.releaseTransactionId()),
                eq(detail.getTransactionDateTime()), eq("M-1"), eq("USD"),
                eq(LocalDate.of(2026, 8, 26)), eq(LocalDateTime.of(2026, 8, 26, 10, 30)));
    }

    /** 已经返还或释放完毕的状态必须幂等跳过，不追加第二条保证金事实。 */
    @Test
    void releaseShouldSkipAlreadyFinalReserveState() {
        LocalDateTime originalTime = LocalDateTime.of(2026, 1, 1, 10, 0);
        ClearingReserveStateDO state = state(originalTime);
        state.setReserveStatus("FULLY_RETURNED");
        state.setRemainingAmount(BigDecimal.ZERO.setScale(2));
        when(reserveMapper.selectStateForUpdate("PAY-1", originalTime)).thenReturn(state);

        var result = service.release("RS-1", "PAY-1", originalTime,
                Instant.parse("2026-08-26T10:30:00Z"));

        assertThat(result.outcome()).isEqualTo(ReserveReleaseOutcome.ALREADY_FINAL);
        verifyNoInteractions(profileMapper, candidateService);
    }

    private ClearingReserveStateDO state(LocalDateTime originalTime) {
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
        row.setRemainingAmount(new BigDecimal("8.00"));
        row.setExpectedReserveReleaseDate(LocalDate.of(2026, 8, 26));
        row.setReserveStatus("OPEN");
        row.setTransactionDateTime(originalTime);
        row.setOriginalTransactionUtcTime(LocalDateTime.of(2026, 1, 1, 2, 0));
        row.setTransactionTimeZone("Asia/Shanghai");
        row.setVersion(3L);
        return row;
    }

    private ClearingReserveDetailDO hold(LocalDateTime originalTime) {
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
        row.setOriginalTransactionDateTime(originalTime);
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
