package com.scott.payment.finance.settlement.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import static com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import static com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import static com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementRateModelsTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 验证结算汇率矩阵的币种规范化、不可变性、批次单目标币种和币种对唯一约束。
 * @status : create
 */
class SettlementRateModelsTest {

    private static final LocalDateTime EFFECTIVE_TIME = LocalDateTime.of(2026, 8, 26, 12, 0);

    @Test
    void matrixShouldNormalizeCurrencyAndReturnLockedPair() {
        LockedRate rate = rate("eur", "usd", "1.100000000000", 2, 2);

        RateMatrix matrix = RateMatrix.of(List.of(rate));

        assertThat(matrix.require("EUR", "USD")).isEqualTo(rate);
        assertThat(matrix.targetCurrency()).isEqualTo("USD");
        assertThat(matrix.rates()).containsExactly(rate);
    }

    @Test
    void matrixShouldRejectDuplicatePairsAndMixedTargets() {
        LockedRate eurUsd = rate("EUR", "USD", "1.100000000000", 2, 2);

        assertThatThrownBy(() -> RateMatrix.of(List.of(eurUsd, eurUsd)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> RateMatrix.of(List.of(
                eurUsd,
                rate("GBP", "EUR", "1.200000000000", 2, 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target currency");
    }

    @Test
    void matrixShouldBeImmutableAndRejectMissingPair() {
        RateMatrix matrix = RateMatrix.of(List.of(rate("EUR", "USD", "1.100000000000", 2, 2)));

        assertThatThrownBy(() -> matrix.rates().add(rate("GBP", "USD", "1.2", 2, 2)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> matrix.require("JPY", "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPY/USD");
    }

    @Test
    void lockedRateShouldRejectValuesOutsideDatabaseDecimalCapacity() {
        assertThatThrownBy(() -> rate("EUR", "USD", "1.1234567890123", 2, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 decimal places");
        assertThatThrownBy(() -> rate("EUR", "USD", "1234567890123.000000000000", 2, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DECIMAL(24,12)");
    }

    private LockedRate rate(String source, String target, String value, int sourceExponent, int targetExponent) {
        return new LockedRate(
                new CurrencyPair(source, target),
                new BigDecimal(value),
                sourceExponent,
                targetExponent,
                "ECB",
                "QUOTE-1",
                QuoteDirection.DIRECT,
                EFFECTIVE_TIME);
    }
}
