package com.scott.payment.component.core.iso;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoStandardResolverTests
 * @date : 2026-06-02 16:05
 * @email : scott_x@163.com
 * @description : ISO 国家与币种工具单元测试
 * @status : create
 */
class IsoStandardResolverTests {

    /**
     * 校验国家工具可以识别两位字母、三位字母、三位数字、英文名称和中文名称。
     */
    @Test
    void shouldResolveCountryByMultipleIsoRepresentations() {
        IsoCountryInfo unitedStates = IsoCountryResolver.resolve("840").orElseThrow();
        assertThat(unitedStates.alpha2()).isEqualTo("US");
        assertThat(IsoCountryResolver.resolve("USA").orElseThrow().numeric()).isEqualTo("840");
        assertThat(IsoCountryResolver.resolve("United States").orElseThrow().alpha3()).isEqualTo("USA");
        assertThat(IsoCountryResolver.resolve("美国").orElseThrow().continentCode()).isEqualTo("NA");

        IsoCountryInfo china = IsoCountryResolver.resolve("中国大陆").orElseThrow();
        assertThat(china.alpha2()).isEqualTo("CN");
        assertThat(china.numeric()).isEqualTo("156");
    }

    /**
     * 校验浏览器语言头可以匹配系统支持的语言和地区。
     */
    @Test
    void shouldResolveBrowserLanguageAndCountry() {
        Locale locale = IsoCountryResolver.matchBrowserLocale(
                "zh-CN,en-US;q=0.8",
                List.of(Locale.SIMPLIFIED_CHINESE, Locale.US)
        ).orElseThrow();
        assertThat(locale).isEqualTo(Locale.SIMPLIFIED_CHINESE);
        assertThat(IsoCountryResolver.resolveCountryFromBrowserLanguage("en-US,en;q=0.9").orElseThrow().alpha3())
                .isEqualTo("USA");
    }

    /**
     * 校验币种工具可以识别 ISO 4217 三字母码、三数字码、中文名，并按辅币位转换金额。
     */
    @Test
    void shouldResolveCurrencyAndValidateMinorUnit() {
        IsoCurrencyInfo usd = IsoCurrencyResolver.resolve("USD").orElseThrow();
        assertThat(usd.numericCode()).isEqualTo("840");
        assertThat(usd.defaultFractionDigits()).isEqualTo(2);
        assertThat(usd.hasStandardAlpha2Code()).isFalse();
        assertThat(IsoCurrencyResolver.toMinorUnit(new BigDecimal("12.34"), usd)).isEqualTo(1234L);

        IsoCurrencyInfo cny = IsoCurrencyResolver.resolve("156").orElseThrow();
        assertThat(cny.alphabeticCode()).isEqualTo("CNY");
        assertThat(IsoCurrencyResolver.resolve("人民币").orElseThrow().alphabeticCode()).isEqualTo("CNY");

        IsoCurrencyInfo jpy = IsoCurrencyResolver.resolve("JPY").orElseThrow();
        assertThat(jpy.defaultFractionDigits()).isZero();
        assertThatThrownBy(() -> IsoCurrencyResolver.toMinorUnit(new BigDecimal("100.01"), jpy))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
