package com.scott.payment.openapi;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoDictionaryServiceTests
 * @date : 2026-06-03 15:35
 * @email : scott_x@163.com
 * @description : ISO 国家地区与币种公共服务数据库查询测试
 * @status : create
 */
@Slf4j
@ActiveProfiles("mysql-test")
@SpringBootTest(classes = OpenApiApplication.class)
class IsoDictionaryServiceTests {

    /**
     * ISO 字典公共服务，后续各微服务可直接注入该服务查询国家和币种。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 创建 ISO 字典公共服务测试。
     *
     * @param isoDictionaryService ISO 字典公共服务
     */
    @Autowired
    IsoDictionaryServiceTests(IsoDictionaryService isoDictionaryService) {
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 验证国家地区全量查询、关键字查询、大洲过滤和默认币种过滤。
     */
    @Test
    void shouldQueryCountriesByDifferentConditions() {
        List<IsoCountryInfo> allCountries = isoDictionaryService.listCountries();
        List<IsoCountryInfo> unitedStates = isoDictionaryService.searchCountries("United States");
        List<IsoCountryInfo> northAmericaCountries = isoDictionaryService.listCountriesByContinent("NA");
        List<IsoCountryInfo> usdCountries = isoDictionaryService.listCountriesByCurrency("USD");

        log.info("ISO国家地区-全量数量：{}", allCountries.size());
        log.info("ISO国家地区-按英文名查询United States：{}", unitedStates);
        log.info("ISO国家地区-北美洲数量：{}", northAmericaCountries.size());
        log.info("ISO国家地区-默认USD币种国家数量：{}", usdCountries.size());

        assertThat(allCountries).hasSizeGreaterThan(200);
        assertThat(isoDictionaryService.getCountry("840")).map(IsoCountryInfo::alpha3).contains("USA");
        assertThat(isoDictionaryService.getCountry("CN")).map(IsoCountryInfo::alpha3).contains("CHN");
        assertThat(northAmericaCountries).extracting(IsoCountryInfo::continentCode).containsOnly("NA");
        assertThat(usdCountries).extracting(IsoCountryInfo::currencyAlpha3Code).contains("USD");
    }

    /**
     * 验证币种全量查询、关键字查询、精确识别、辅币位校验和主币转最小单位。
     */
    @Test
    void shouldQueryCurrenciesAndValidateMinorUnit() {
        List<IsoCurrencyInfo> allCurrencies = isoDictionaryService.listCurrencies();
        List<IsoCurrencyInfo> usdCurrencies = isoDictionaryService.searchCurrencies("USD");
        boolean validUsdAmount = isoDictionaryService.isCurrencyFractionValid(new BigDecimal("12.34"), "USD");
        boolean invalidJpyAmount = isoDictionaryService.isCurrencyFractionValid(new BigDecimal("12.34"), "JPY");
        long usdMinorAmount = isoDictionaryService.toMinorUnit(new BigDecimal("12.34"), "USD");

        log.info("ISO币种-全量数量：{}", allCurrencies.size());
        log.info("ISO币种-按USD查询结果：{}", usdCurrencies);
        log.info("ISO币种-USD金额12.34辅币位是否合法：{}", validUsdAmount);
        log.info("ISO币种-JPY金额12.34辅币位是否合法：{}", invalidJpyAmount);
        log.info("ISO币种-USD金额12.34转最小单位：{}", usdMinorAmount);

        assertThat(allCurrencies).hasSizeGreaterThan(150);
        assertThat(isoDictionaryService.getCurrency("840")).map(IsoCurrencyInfo::alphabeticCode).contains("USD");
        assertThat(isoDictionaryService.getCurrency("人民币")).map(IsoCurrencyInfo::alphabeticCode).contains("CNY");
        assertThat(validUsdAmount).isTrue();
        assertThat(invalidJpyAmount).isFalse();
        assertThat(usdMinorAmount).isEqualTo(1234L);
    }
}
