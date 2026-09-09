package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.mapper.ClearingSettlementEligibilityMapper;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeCurrencyPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.PercentageBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.RefundFeeReturnPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReservePolicySnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveRefundPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.SettlementPolicySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证交易结算首次周期和常规周期的工作日计算。 */
class DefaultSettlementEligibilityServiceTest {

    private static final String MERCHANT_ID = "200045";
    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 9, 5);

    @Test
    void calculateShouldUseInitialWorkdayDelayBeforeFirstPostedSettlement() {
        ClearingSettlementEligibilityMapper mapper = mock(ClearingSettlementEligibilityMapper.class);
        DefaultSettlementEligibilityService service = new DefaultSettlementEligibilityService(mapper);
        LocalDate eligibleDate = LocalDate.of(2026, 9, 9);
        when(mapper.countPostedRegularSettlement(MERCHANT_ID)).thenReturn(0);
        when(mapper.selectNthConfirmedWorkday(TRANSACTION_DATE, 2)).thenReturn(eligibleDate);
        when(mapper.countConfirmedCalendarDays(TRANSACTION_DATE, eligibleDate)).thenReturn(4);

        assertThat(service.calculate(MERCHANT_ID, TRANSACTION_DATE, snapshot(3, 1)))
                .isEqualTo(eligibleDate);

        verify(mapper).selectNthConfirmedWorkday(TRANSACTION_DATE, 2);
    }

    @Test
    void calculateShouldUseRegularWorkdayDelayAfterPostedSettlement() {
        ClearingSettlementEligibilityMapper mapper = mock(ClearingSettlementEligibilityMapper.class);
        DefaultSettlementEligibilityService service = new DefaultSettlementEligibilityService(mapper);
        LocalDate eligibleDate = LocalDate.of(2026, 9, 7);
        when(mapper.countPostedRegularSettlement(MERCHANT_ID)).thenReturn(1);
        when(mapper.selectNthConfirmedWorkday(TRANSACTION_DATE, 0)).thenReturn(eligibleDate);
        when(mapper.countConfirmedCalendarDays(TRANSACTION_DATE, eligibleDate)).thenReturn(2);

        assertThat(service.calculate(MERCHANT_ID, TRANSACTION_DATE, snapshot(3, 1)))
                .isEqualTo(eligibleDate);

        verify(mapper).selectNthConfirmedWorkday(TRANSACTION_DATE, 0);
    }

    private FeeVersionSnapshot snapshot(int initialDelayDays, int regularDelayDays) {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                1L, FeeMode.STANDARD, BigDecimal.ONE, null, null, null, null);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                1L, "TRANSACTION_FEE", "PAYMENT", "ALL", "ALL", "NONE",
                "NOT_APPLICABLE", rule, List.of());
        return new FeeVersionSnapshot(
                4, MERCHANT_ID, 1L, 6L, 1,
                LocalDateTime.of(2026, 9, 1, 0, 0), "USD",
                new SettlementPolicySnapshot(
                        "T", initialDelayDays, regularDelayDays, "DAILY", null,
                        LocalDate.of(2026, 9, 1)),
                PercentageBasis.LABEL_AMOUNT,
                FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                new ReservePolicySnapshot(
                        BigDecimal.ZERO, ReserveBasis.LABEL_AMOUNT, "D", 180,
                        ReserveRefundPolicy.PROPORTIONAL_RETURN),
                RefundFeeReturnPolicy.NONE,
                List.of(configuredRule),
                "a".repeat(64));
    }
}
