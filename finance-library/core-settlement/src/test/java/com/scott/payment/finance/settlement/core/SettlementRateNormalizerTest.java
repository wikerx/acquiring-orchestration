package com.scott.payment.finance.settlement.core;

import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementRateNormalizerTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 验证 DIRECT、INVERSE 和同币种报价统一固化为一单位源币种对应目标币种的批次直接汇率。
 * @status : create
 */
class SettlementRateNormalizerTest {

    private final SettlementRateNormalizer normalizer = new SettlementRateNormalizer();
    private final LocalDateTime effectiveTime = LocalDateTime.of(2026, 8, 26, 12, 0);

    @Test
    void shouldNormalizeDirectAndInverseQuotesToTwelveDecimalPlaces() {
        LockedRate direct = normalizer.normalize(quote("EUR", "USD", "1.2345678901234", QuoteDirection.DIRECT), 2, 2);
        LockedRate inverse = normalizer.normalize(quote("EUR", "USD", "0.8", QuoteDirection.INVERSE), 2, 2);

        assertThat(direct.directRate()).isEqualByComparingTo("1.234567890123");
        assertThat(inverse.directRate()).isEqualByComparingTo("1.250000000000");
        assertThat(inverse.sourceQuoteDirection()).isEqualTo(QuoteDirection.INVERSE);
    }

    @Test
    void shouldCreateStrictIdentityRateForSameCurrency() {
        LockedRate identity = normalizer.identity("jpy", 0, effectiveTime);

        assertThat(identity.pair()).isEqualTo(new CurrencyPair("JPY", "JPY"));
        assertThat(identity.directRate()).isEqualByComparingTo("1.000000000000");
        assertThat(identity.rateSource()).isEqualTo("SYSTEM_IDENTITY");
    }

    @Test
    void shouldRejectNonIdentityQuoteForSameCurrency() {
        assertThatThrownBy(() -> normalizer.normalize(
                quote("USD", "USD", "1.01", QuoteDirection.DIRECT), 2, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity");
    }

    private RateQuote quote(String source, String target, String rate, QuoteDirection direction) {
        return new RateQuote(new CurrencyPair(source, target), new BigDecimal(rate), direction,
                "ECB", "QUOTE-1", effectiveTime);
    }
}
