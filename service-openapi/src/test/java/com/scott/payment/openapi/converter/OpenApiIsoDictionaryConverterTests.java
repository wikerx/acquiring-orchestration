package com.scott.payment.openapi.converter;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoDictionaryConverterTests
 * @date : 2026-09-30 00:00
 * @email : scott_x@163.com
 * @description : 验证 ISO 字典 MapStruct 响应映射的代码、金额精度及空值语义。
 * @status : create
 */
class OpenApiIsoDictionaryConverterTests {

    private final OpenApiIsoDictionaryConverter converter = OpenApiIsoDictionaryConverter.INSTANCE;

    @Test
    void shouldPreserveCountryCodesAndNullableFields() {
        IsoCountryInfo country = new IsoCountryInfo("AF", "AFG", "004", "Afghanistan", "Afghanistan",
                "阿富汗", "AS", "亚洲", null, null, null, null, "AFN");

        IsoCountryVO result = converter.toCountryVO(country);

        assertThat(result.getAlpha2()).isEqualTo("AF");
        assertThat(result.getAlpha3()).isEqualTo("AFG");
        assertThat(result.getNumeric()).isEqualTo("004");
        assertThat(result.getEnglishName()).isEqualTo("Afghanistan");
        assertThat(result.getChineseName()).isEqualTo("阿富汗");
        assertThat(result.getCurrencyAlpha3Code()).isEqualTo("AFN");
        assertThat(result.getFlagEmoji()).isNull();
        assertThat(result.getPrimaryLanguageCode()).isNull();
    }

    @Test
    void shouldPreserveCurrencyValuesAndNullableFields() {
        IsoCurrencyInfo currency = new IsoCurrencyInfo("ALL", "008", "Albanian Lek", null,
                2, 100, new BigDecimal("0.01"), null);

        IsoCurrencyVO result = converter.toCurrencyVO(currency);

        assertThat(result.getAlphabeticCode()).isEqualTo("ALL");
        assertThat(result.getNumericCode()).isEqualTo("008");
        assertThat(result.getDefaultFractionDigits()).isEqualTo(2);
        assertThat(result.getMinorUnitMultiplier()).isEqualTo(100L);
        assertThat(result.getMinimumAmount()).isEqualByComparingTo("0.01");
        assertThat(result.getChineseName()).isNull();
        assertThat(result.getCurrencySymbol()).isNull();
    }

    @Test
    void shouldReturnNullForNullSource() {
        assertThat(converter.toCountryVO(null)).isNull();
        assertThat(converter.toCurrencyVO(null)).isNull();
    }
}
