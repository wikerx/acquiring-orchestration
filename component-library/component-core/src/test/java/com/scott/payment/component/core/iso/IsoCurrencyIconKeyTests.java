package com.scott.payment.component.core.iso;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 币种展示图标键白名单和币种一致性测试。
 */
class IsoCurrencyIconKeyTests {

    @Test
    void shouldNormalizeSupportedManagedKeys() {
        assertThat(IsoCurrencyIconKey.normalize("USD", " flag:us ")).isEqualTo("flag:US");
        assertThat(IsoCurrencyIconKey.normalize("xau", " currency:xau ")).isEqualTo("currency:XAU");
        assertThat(IsoCurrencyIconKey.normalize("USD", " ")).isNull();
        assertThat(IsoCurrencyIconKey.normalize("USD", null)).isNull();
    }

    @Test
    void shouldRejectUnmanagedOrMismatchedKeys() {
        assertThatThrownBy(() -> IsoCurrencyIconKey.normalize("USD", "currency:CNY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match");

        for (String unsafeKey : new String[]{
                "https://cdn.example.com/usd.svg",
                "<img src=x onerror=alert(1)>",
                "/assets/currency/usd.svg",
                "flag:USA"
        }) {
            assertThatThrownBy(() -> IsoCurrencyIconKey.normalize("USD", unsafeKey))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("flag:XX or currency:CODE");
        }
    }
}
