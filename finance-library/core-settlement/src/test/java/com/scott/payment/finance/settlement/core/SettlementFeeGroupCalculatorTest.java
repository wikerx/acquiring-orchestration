package com.scott.payment.finance.settlement.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.AppliedLimit;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeComponentInput;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeComponentKind;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeGroupCommand;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeGroupResult;
import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementFeeGroupCalculatorTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 验证标签币种百分比组件与 USD 固定费、最低费和最高费只在结算批次目标币种统一求值。
 * @status : create
 */
class SettlementFeeGroupCalculatorTest {

    private final SettlementFeeGroupCalculator calculator = new SettlementFeeGroupCalculator();

    @Test
    void shouldCombineLabelPercentageAndUsdFixedWithoutApplyingLimit() {
        FeeGroupResult result = calculator.calculate(command("2.30", "0.30", "0.50", "5.00"), matrix());

        assertThat(result.unroundedCalculatedAmount()).isEqualByComparingTo("2.83000000000000");
        assertThat(result.appliedLimit()).isEqualTo(AppliedLimit.NONE);
        assertThat(result.finalFee().amount()).isEqualByComparingTo("2.83");
        assertThat(result.limitAdjustmentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldApplyUsdMinimumAfterUsingSameBatchRate() {
        FeeGroupResult result = calculator.calculate(command("0.10", "0.30", "0.50", "5.00"), matrix());

        assertThat(result.unroundedCalculatedAmount()).isEqualByComparingTo("0.41000000000000");
        assertThat(result.unroundedMinimumAmount()).isEqualByComparingTo("0.50000000000000");
        assertThat(result.appliedLimit()).isEqualTo(AppliedLimit.MINIMUM);
        assertThat(result.limitAdjustmentAmount()).isEqualByComparingTo("0.09000000000000");
        assertThat(result.finalFee().amount()).isEqualByComparingTo("0.50");
    }

    @Test
    void shouldApplyUsdMaximumWithoutChangingClearingComponents() {
        FeeGroupResult result = calculator.calculate(command("10.00", "0.30", "0.50", "5.00"), matrix());

        assertThat(result.unroundedCalculatedAmount()).isEqualByComparingTo("11.30000000000000");
        assertThat(result.appliedLimit()).isEqualTo(AppliedLimit.MAXIMUM);
        assertThat(result.limitAdjustmentAmount()).isEqualByComparingTo("-6.30000000000000");
        assertThat(result.finalFee().amount()).isEqualByComparingTo("5.00");
        assertThat(result.convertedComponents()).hasSize(2);
    }

    @Test
    void shouldUseOneKwdBatchMatrixForLabelPercentageAndAllUsdFacts() {
        FeeGroupCommand command = new FeeGroupCommand("FG-KWD", List.of(
                new FeeComponentInput("PERCENTAGE-EUR", FeeComponentKind.PERCENTAGE,
                        new Money(new BigDecimal("1.00"), "EUR", 2)),
                new FeeComponentInput("FIXED-USD", FeeComponentKind.FIXED,
                        new Money(new BigDecimal("1.00"), "USD", 2))),
                new Money(new BigDecimal("3.00"), "USD", 2),
                new Money(new BigDecimal("4.00"), "USD", 2),
                "KWD", 3, RoundingMode.HALF_UP);
        RateMatrix matrix = RateMatrix.of(List.of(
                rate("EUR", "KWD", "0.333333333333", "ECB", 2, 3),
                rate("USD", "KWD", "0.307692307692", "ECB", 2, 3)));

        FeeGroupResult result = calculator.calculate(command, matrix);

        assertThat(result.unroundedCalculatedAmount()).isEqualByComparingTo("0.64102564102500");
        assertThat(result.unroundedMinimumAmount()).isEqualByComparingTo("0.92307692307600");
        assertThat(result.unroundedMaximumAmount()).isEqualByComparingTo("1.23076923076800");
        assertThat(result.appliedLimit()).isEqualTo(AppliedLimit.MINIMUM);
        assertThat(result.finalFee().currency()).isEqualTo("KWD");
        assertThat(result.finalFee().amount()).isEqualByComparingTo("0.923");
        assertThat(result.finalFee().exponent()).isEqualTo(3);
    }

    private FeeGroupCommand command(String percentageEur, String fixedUsd, String minimumUsd, String maximumUsd) {
        return new FeeGroupCommand("FG-1", List.of(
                new FeeComponentInput("PERCENTAGE-1", FeeComponentKind.PERCENTAGE,
                        new Money(new BigDecimal(percentageEur), "EUR", 2)),
                new FeeComponentInput("FIXED-1", FeeComponentKind.FIXED,
                        new Money(new BigDecimal(fixedUsd), "USD", 2))),
                new Money(new BigDecimal(minimumUsd), "USD", 2),
                new Money(new BigDecimal(maximumUsd), "USD", 2),
                "USD", 2, RoundingMode.HALF_UP);
    }

    private RateMatrix matrix() {
        return RateMatrix.of(List.of(
                rate("EUR", "USD", "1.100000000000", "ECB"),
                rate("USD", "USD", "1.000000000000", "SYSTEM_IDENTITY")));
    }

    private LockedRate rate(String source, String target, String value, String sourceName) {
        return rate(source, target, value, sourceName, 2, 2);
    }

    private LockedRate rate(String source, String target, String value, String sourceName,
                            int sourceExponent, int targetExponent) {
        return new LockedRate(new CurrencyPair(source, target), new BigDecimal(value),
                sourceExponent, targetExponent,
                sourceName, source.equals(target) ? null : "QUOTE-1", QuoteDirection.DIRECT,
                LocalDateTime.of(2026, 8, 26, 12, 0));
    }
}
