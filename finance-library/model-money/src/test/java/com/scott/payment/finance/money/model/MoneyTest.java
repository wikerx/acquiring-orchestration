package com.scott.payment.finance.money.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MoneyTest
 * @date : 2026-08-25 00:00
 * @email : scott_x@163.com
 * @description : 验证跨财务域金额值对象的币种规范化、显式舍入和有符号净额表达能力。
 * @status : create
 */
class MoneyTest {

    /** 通用金额允许表达负净额，并将币种代码规范化为 ISO 大写形式。 */
    @Test
    void shouldRepresentSignedAmountAndNormalizeCurrency() {
        Money money = new Money(new BigDecimal("-12.345"), " usd ", 2);

        assertThat(money.amount()).isEqualByComparingTo("-12.345");
        assertThat(money.currency()).isEqualTo("USD");
        assertThat(money.rounded(RoundingMode.HALF_UP).amount()).isEqualByComparingTo("-12.35");
    }

    /** 非法币种和 exponent 必须在进入费用、保证金及结算领域前被拒绝。 */
    @Test
    void shouldRejectInvalidCurrencyMetadata() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "US", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO 4217");
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "USD", 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 8");
    }
}
