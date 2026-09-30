package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;
import com.scott.payment.openapi.support.OpenApiPayloadDecoder;
import com.scott.payment.openapi.support.OpenApiValidator;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.security.PrivateKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiIsoCurrencyQueryTests
 * @date : 2026-09-30 00:00
 * @email : scott_x@163.com
 * @description : 验证商户币种查询只接受 ISO 代码，条件查询走数据库且无条件查询走全量快照。
 * @status : create
 */
class OpenApiIsoCurrencyQueryTests {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final IsoCurrencyInfo USD = new IsoCurrencyInfo("USD", "840", "US Dollar", "美元", 2,
            100, new BigDecimal("0.01"), "$");
    private static final IsoCurrencyInfo ALL = new IsoCurrencyInfo("ALL", "008", "Albanian Lek", "阿尔巴尼亚列克", 2,
            100, new BigDecimal("0.01"), "L");
    private final IsoDictionaryService dictionaryService = mock(IsoDictionaryService.class);
    private final OpenApiIsoDictionaryServiceImpl service = new OpenApiIsoDictionaryServiceImpl(dictionaryService);
    private final OpenApiPayloadCrypto crypto = mock(OpenApiPayloadCrypto.class);
    private final OpenApiPayloadKeyProvider keyProvider = mock(OpenApiPayloadKeyProvider.class);
    private final OpenApiPayloadDecoder decoder = new OpenApiPayloadDecoder(crypto, keyProvider);
    private final OpenApiValidator validator = new OpenApiValidator(VALIDATOR_FACTORY.getValidator());

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @ParameterizedTest
    @CsvSource({"alphabeticCode, USD, USD", "numericCode, 840, USD", "numericCode, 008, ALL"})
    void shouldRouteSingleCodeToExactQuery(String field, String value, String expectedCode) {
        String alphabeticCode = "alphabeticCode".equals(field) ? value : null;
        String numericCode = "numericCode".equals(field) ? value : null;
        when(dictionaryService.listCurrenciesByCodes(alphabeticCode, numericCode))
                .thenReturn(List.of("ALL".equals(expectedCode) ? ALL : USD));

        assertThat(query("{\"" + field + "\":\"" + value + "\"}"))
                .extracting(IsoCurrencyVO::getAlphabeticCode).containsExactly(expectedCode);
        verify(dictionaryService).listCurrenciesByCodes(alphabeticCode, numericCode);
        verify(dictionaryService, never()).listCurrencies();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"alphabeticCode\":null,\"numericCode\":null}"})
    void shouldReturnFullCurrencyListWhenNoCodesAreProvided(String json) {
        when(dictionaryService.listCurrencies()).thenReturn(List.of(USD));

        assertThat(query(json)).extracting(IsoCurrencyVO::getAlphabeticCode).containsExactly("USD");
        verify(dictionaryService).listCurrencies();
        verify(dictionaryService, never()).listCurrenciesByCodes(null, null);
    }

    @Test
    void shouldCombineCodesAndReturnEmptyForConflictingCodes() {
        when(dictionaryService.listCurrenciesByCodes("USD", "840")).thenReturn(List.of(USD));
        when(dictionaryService.listCurrenciesByCodes("USD", "156")).thenReturn(List.of());

        assertThat(query("{\"alphabeticCode\":\"USD\",\"numericCode\":\"840\"}"))
                .extracting(IsoCurrencyVO::getNumericCode).containsExactly("840");
        assertThat(query("{\"alphabeticCode\":\"USD\",\"numericCode\":\"156\"}"))
                .isEmpty();
        verify(dictionaryService, never()).listCurrencies();
    }

    @ParameterizedTest
    @ValueSource(strings = {"englishName", "chineseName", "currencySymbol", "unknownField", "@type"})
    void shouldRejectUnsupportedFields(String field) {
        assertInvalid("{\"" + field + "\":\"unused\"}");
        assertInvalid("{\"alphabeticCode\":\"USD\",\"" + field + "\":null}");
        verifyNoInteractions(dictionaryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"alphabeticCode\":\"\"}", "{\"alphabeticCode\":\"usd\"}",
            "{\"alphabeticCode\":\"US\"}", "{\"numericCode\":\"84\"}",
            "{\"numericCode\":\"84A\"}", "{\"numericCode\":840}",
            "{\"alphabeticCode\":true}", "[]", "null", "{"
    })
    void shouldRejectInvalidCodeValues(String json) {
        assertInvalid(json);
        verifyNoInteractions(dictionaryService);
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> query(json))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
    }

    private List<IsoCurrencyVO> query(String json) {
        OpenApiRequestHeaderDTO header = new OpenApiRequestHeaderDTO();
        header.setMerchantId("test-merchant");
        PrivateKey privateKey = mock(PrivateKey.class);
        when(keyProvider.getPlatformPrivateKey("test-merchant")).thenReturn(privateKey);
        when(crypto.decrypt("test-cipher", privateKey)).thenReturn(json);
        IsoCurrencyQueryRequestDTO request = (IsoCurrencyQueryRequestDTO) decoder.decode(
                "{\"data\":\"test-cipher\"}", IsoCurrencyQueryRequestDTO.class, header);
        validator.validate(request);
        return service.queryCurrencies(request);
    }
}
