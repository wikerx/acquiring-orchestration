package com.scott.payment.finance.settlement.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.AmountDirection;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.AmountLine;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.ConversionCommand;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.ConversionResult;
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
 * @classname : SettlementAmountCalculatorTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 验证多原币种结算按统一批次汇率计算，保持行级未舍入值并只对最终目标净额舍入一次。
 * @status : create
 */
class SettlementAmountCalculatorTest {

    private final SettlementAmountCalculator calculator = new SettlementAmountCalculator();

    @Test
    void shouldConvertCreditAndDebitLinesWithOneBatchMatrix() {
        RateMatrix matrix = RateMatrix.of(List.of(
                rate("EUR", "USD", "1.100000000000", 2, 2),
                rate("USD", "USD", "1.000000000000", 2, 2)));
        ConversionCommand command = new ConversionCommand(List.of(
                new AmountLine("PRINCIPAL-1", new Money(new BigDecimal("100.00"), "EUR", 2), AmountDirection.CREDIT),
                new AmountLine("FEE-1", new Money(new BigDecimal("0.30"), "USD", 2), AmountDirection.DEBIT)),
                "USD", 2, RoundingMode.HALF_UP);

        ConversionResult result = calculator.calculate(command, matrix);

        assertThat(result.unroundedTargetNetAmount()).isEqualByComparingTo("109.70000000000000");
        assertThat(result.targetNetAmount().amount()).isEqualByComparingTo("109.70");
        assertThat(result.convertedLines()).extracting(line -> line.unroundedTargetAmount())
                .containsExactly(new BigDecimal("110.00000000000000"), new BigDecimal("0.30000000000000"));
    }

    @Test
    void shouldRoundOnlyAfterAggregatingAllLines() {
        RateMatrix matrix = RateMatrix.of(List.of(rate("USD", "USD", "1.000000000000", 2, 2)));
        ConversionCommand command = new ConversionCommand(List.of(
                new AmountLine("A", new Money(new BigDecimal("0.005"), "USD", 2), AmountDirection.CREDIT),
                new AmountLine("B", new Money(new BigDecimal("0.005"), "USD", 2), AmountDirection.CREDIT)),
                "USD", 2, RoundingMode.HALF_UP);

        ConversionResult result = calculator.calculate(command, matrix);

        assertThat(result.unroundedTargetNetAmount()).isEqualByComparingTo("0.010000000000000");
        assertThat(result.targetNetAmount().amount()).isEqualByComparingTo("0.01");
    }

    @Test
    void shouldAggregateBeforeRoundingToZeroDecimalJpy() {
        RateMatrix matrix = RateMatrix.of(List.of(rate("USD", "JPY", "150.000000000000", 2, 0)));
        ConversionCommand command = new ConversionCommand(List.of(
                new AmountLine("A", new Money(new BigDecimal("0.003333333333"), "USD", 2), AmountDirection.CREDIT),
                new AmountLine("B", new Money(new BigDecimal("0.003333333333"), "USD", 2), AmountDirection.CREDIT)),
                "JPY", 0, RoundingMode.HALF_UP);

        ConversionResult result = calculator.calculate(command, matrix);

        assertThat(result.unroundedTargetNetAmount()).isEqualByComparingTo("0.999999999900000000000000");
        assertThat(result.targetNetAmount().amount()).isEqualByComparingTo("1");
        assertThat(result.targetNetAmount().exponent()).isZero();
    }

    private LockedRate rate(String source, String target, String value, int sourceExponent, int targetExponent) {
        return new LockedRate(new CurrencyPair(source, target), new BigDecimal(value), sourceExponent,
                targetExponent, source.equals(target) ? "SYSTEM_IDENTITY" : "ECB",
                source.equals(target) ? null : "QUOTE-1", QuoteDirection.DIRECT,
                LocalDateTime.of(2026, 8, 26, 12, 0));
    }
}
