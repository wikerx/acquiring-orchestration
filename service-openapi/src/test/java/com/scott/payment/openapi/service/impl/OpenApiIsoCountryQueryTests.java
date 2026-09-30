package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.dto.body.iso.IsoCountryQueryRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;
import com.scott.payment.openapi.support.OpenApiPayloadDecoder;
import com.scott.payment.openapi.support.OpenApiValidator;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.PrivateKey;
import java.util.List;
import java.util.stream.Stream;

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
 * @classname : OpenApiIsoCountryQueryTests
 * @date : 2026-09-30 00:00
 * @email : scott_x@163.com
 * @description : 验证国家查询的三代码契约与查询路由，隔离外部密钥、Redis 和数据库。
 * @status : create
 */
@Slf4j
class OpenApiIsoCountryQueryTests {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final List<IsoCountryInfo> COUNTRIES = List.of(
            country("US", "USA", "840", "United States of America", "美国"),
            country("CN", "CHN", "156", "China", "中国"),
            country("AF", "AFG", "004", "Afghanistan", "阿富汗")
    );

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
    @CsvSource({"alpha2, US, USA", "alpha3, USA, USA", "numeric, 840, USA", "numeric, 004, AFG"})
    void shouldMatchEachCodeExactly(String field, String value, String expectedAlpha3) {
        log.info("验证国家代码精确查询，字段: {}，代码: {}，预期国家: {}", field, value, expectedAlpha3);
        IsoCountryInfo matched = COUNTRIES.stream()
                .filter(country -> expectedAlpha3.equals(country.alpha3()))
                .findFirst()
                .orElseThrow();
        String alpha2 = "alpha2".equals(field) ? value : null;
        String alpha3 = "alpha3".equals(field) ? value : null;
        String numeric = "numeric".equals(field) ? value : null;
        when(dictionaryService.listCountriesByCodes(alpha2, alpha3, numeric)).thenReturn(List.of(matched));

        List<IsoCountryVO> result = query("{\"" + field + "\":\"" + value + "\"}");

        assertThat(result).extracting(IsoCountryVO::getAlpha3).containsExactly(expectedAlpha3);
        verify(dictionaryService).listCountriesByCodes(alpha2, alpha3, numeric);
        verify(dictionaryService, never()).listCountries();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"alpha2\":null,\"alpha3\":null,\"numeric\":null}"})
    void shouldReturnAllCountriesWhenCodesAreAbsent(String json) {
        log.info("验证无国家代码时返回全部启用国家，预期数量: {}", COUNTRIES.size());
        when(dictionaryService.listCountries()).thenReturn(COUNTRIES);

        assertThat(query(json)).extracting(IsoCountryVO::getAlpha3).containsExactly("USA", "CHN", "AFG");
        verify(dictionaryService).listCountries();
        verify(dictionaryService, never()).listCountriesByCodes(null, null, null);
    }

    @Test
    void shouldRequireAllSuppliedCodesToMatchTheSameCountry() {
        log.info("验证多个国家代码按交集匹配，预期美国唯一命中且响应名称完整");
        when(dictionaryService.listCountriesByCodes("US", "USA", "840"))
                .thenReturn(List.of(COUNTRIES.get(0)));

        List<IsoCountryVO> result = query("{\"alpha2\":\"US\",\"alpha3\":\"USA\",\"numeric\":\"840\"}");

        assertThat(result).extracting(IsoCountryVO::getAlpha3).containsExactly("USA");
        assertThat(result.get(0).getEnglishName()).isEqualTo("United States of America");
        assertThat(result.get(0).getChineseName()).isEqualTo("美国");
        verify(dictionaryService).listCountriesByCodes("US", "USA", "840");
        verify(dictionaryService, never()).listCountries();
    }

    @ParameterizedTest
    @MethodSource("unmatchedCodeCases")
    void shouldReturnEmptyForConflictingOrUnknownCodes(String json, String alpha2, String alpha3, String numeric) {
        log.info("验证冲突或不存在的国家代码，预期空列表");
        when(dictionaryService.listCountriesByCodes(alpha2, alpha3, numeric)).thenReturn(List.of());

        assertThat(query(json)).isEmpty();
        verify(dictionaryService).listCountriesByCodes(alpha2, alpha3, numeric);
        verify(dictionaryService, never()).listCountries();
    }

    private static Stream<Arguments> unmatchedCodeCases() {
        return Stream.of(
                Arguments.of("{\"alpha2\":\"US\",\"alpha3\":\"CHN\"}", "US", "CHN", null),
                Arguments.of("{\"alpha3\":\"USA\",\"numeric\":\"156\"}", null, "USA", "156"),
                Arguments.of("{\"alpha2\":\"ZZ\"}", "ZZ", null, null),
                Arguments.of("{\"alpha3\":\"ZZZ\"}", null, "ZZZ", null),
                Arguments.of("{\"numeric\":\"999\"}", null, null, "999")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "englishName", "shortEnglishName", "chineseName", "continentCode",
            "primaryLanguageCode", "currencyAlpha3Code", "unknownField", "@type"
    })
    void shouldRejectUnsupportedFieldsBeforeQuerying(String field) {
        log.info("验证国家查询拒绝未开放字段，字段: {}，预期参数错误且不读取字典", field);

        assertParameterInvalid("{\"" + field + "\":\"unused\"}");
        assertParameterInvalid("{\"alpha3\":\"USA\",\"" + field + "\":null}");
        verifyNoInteractions(dictionaryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"alpha2\":\"\"}", "{\"alpha2\":\"us\"}", "{\"alpha2\":\"USA\"}",
            "{\"alpha3\":\"\"}", "{\"alpha3\":\"usa\"}", "{\"alpha3\":\"US\"}",
            "{\"alpha3\":\"US-A\"}", "{\"alpha3\":\" USA \"}",
            "{\"numeric\":\"\"}", "{\"numeric\":\"84\"}", "{\"numeric\":\"84A\"}"
    })
    void shouldRejectMalformedCodesBeforeQuerying(String json) {
        log.info("验证国家代码长度、大小写及数字格式，预期参数错误且不读取字典");

        assertParameterInvalid(json);
        verifyNoInteractions(dictionaryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"numeric\":840}", "{\"alpha2\":true}", "{\"alpha3\":[\"USA\"]}",
            "{\"alpha3\":{\"code\":\"USA\"}}", "[]", "null", "\"USA\"", "{"
    })
    void shouldRejectNonObjectPayloadsAndNonStringCodes(String json) {
        log.info("验证国家查询 JSON 必须是对象且代码必须为字符串，预期参数错误且不读取字典");

        assertParameterInvalid(json);
        verifyNoInteractions(dictionaryService);
    }

    private void assertParameterInvalid(String json) {
        assertThatThrownBy(() -> query(json))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
    }

    private List<IsoCountryVO> query(String json) {
        IsoCountryQueryRequestDTO request = (IsoCountryQueryRequestDTO) decode(json, IsoCountryQueryRequestDTO.class);
        validator.validate(request);
        return service.queryCountries(request);
    }

    private Object decode(String json, Class<?> receiver) {
        OpenApiRequestHeaderDTO header = new OpenApiRequestHeaderDTO();
        header.setMerchantId("test-merchant");
        PrivateKey privateKey = mock(PrivateKey.class);
        when(keyProvider.getPlatformPrivateKey("test-merchant")).thenReturn(privateKey);
        when(crypto.decrypt("test-cipher", privateKey)).thenReturn(json);
        return decoder.decode("{\"data\":\"test-cipher\"}", receiver, header);
    }

    private static IsoCountryInfo country(String alpha2, String alpha3, String numeric,
                                          String englishName, String chineseName) {
        return new IsoCountryInfo(alpha2, alpha3, numeric, englishName, englishName, chineseName,
                null, null, null, null, null, null, null);
    }
}
