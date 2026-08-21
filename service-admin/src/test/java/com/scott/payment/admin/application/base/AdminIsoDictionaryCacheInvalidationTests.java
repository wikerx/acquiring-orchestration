package com.scott.payment.admin.application.base;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.db.iso.service.IsoDictionaryCacheInvalidator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminIsoDictionaryCacheInvalidationTests
 * @date : 2026-07-30 21:55
 * @email : scott_x@163.com
 * @description : 管理端国家、币种和地区币种写操作后的 ISO 字典缓存显式失效测试
 * @status : create
 */
@Slf4j
class AdminIsoDictionaryCacheInvalidationTests {

    /** 所有 ISO 写入口必须在主库事务内绑定数据库修改和缓存 Outbox。 */
    @Test
    void shouldRunAllIsoMutationsInsideMasterTransactions() throws Exception {
        assertMasterTransaction(AdminBaseCountryApplicationService.class,
                "createCountry", IsoCountryDO.class);
        assertMasterTransaction(AdminBaseCountryApplicationService.class,
                "updateCountry", Long.class, IsoCountryDO.class);
        assertMasterTransaction(AdminBaseCountryApplicationService.class,
                "updateStatus", Long.class, Map.class);
        assertMasterTransaction(AdminBaseCountryApplicationService.class,
                "removeCountry", Long.class);
        assertMasterTransaction(AdminBaseCurrencyApplicationService.class,
                "createCurrency", IsoCurrencyDO.class);
        assertMasterTransaction(AdminBaseCurrencyApplicationService.class,
                "updateCurrency", Long.class, IsoCurrencyDO.class);
        assertMasterTransaction(AdminBaseCurrencyApplicationService.class,
                "updateStatus", Long.class, Map.class);
        assertMasterTransaction(AdminBaseCurrencyApplicationService.class,
                "removeCurrency", Long.class);
        assertMasterTransaction(AdminBaseRegionCurrencyApplicationService.class,
                "createRegionCurrency", Map.class);
        assertMasterTransaction(AdminBaseRegionCurrencyApplicationService.class,
                "updateRegionCurrency", Long.class, Map.class);
        assertMasterTransaction(AdminBaseRegionCurrencyApplicationService.class,
                "removeRegionCurrency", Long.class);
        assertMasterTransaction(AdminBaseRegionCurrencyApplicationService.class,
                "updateStatus", Long.class, Map.class);
    }

    /**
     * 新增国家资料持久化后必须失效国家字典缓存。
     */
    @Test
    void shouldEvictCountryCacheAfterCountryCreation() {
        log.info("测试管理端国家资料缓存失效，关键输入: 新增启用国家记录");
        IsoCountryMapper countryMapper = mock(IsoCountryMapper.class);
        IsoDictionaryCacheInvalidator invalidator = mock(IsoDictionaryCacheInvalidator.class);
        AdminBaseCountryApplicationService service = countryService(countryMapper, invalidator);
        IsoCountryDO country = new IsoCountryDO();
        country.setAlpha2Code("US");
        country.setAlpha3Code("USA");

        service.createCountry(country);

        verify(countryMapper).insert(country);
        verify(invalidator).evictCountries();
        log.info("管理端国家资料缓存失效验证完成，结果: insert 后已调用国家缓存失效");
    }

    /**
     * 更新币种状态持久化后必须失效币种字典缓存。
     */
    @Test
    void shouldEvictCurrencyCacheAfterStatusUpdate() {
        log.info("测试管理端币种缓存失效，关键输入: USD 状态更新为停用");
        IsoCurrencyMapper currencyMapper = mock(IsoCurrencyMapper.class);
        IsoDictionaryCacheInvalidator invalidator = mock(IsoDictionaryCacheInvalidator.class);
        IsoCurrencyDO currency = new IsoCurrencyDO();
        currency.setId(1L);
        currency.setAlpha3Code("USD");
        currency.setStatus(1);
        when(currencyMapper.selectById(1L)).thenReturn(currency);
        AdminBaseCurrencyApplicationService service = currencyService(currencyMapper, invalidator);

        service.updateStatus(1L, Map.of("status", 0));

        verify(currencyMapper).updateById(currency);
        verify(invalidator).evictCurrencies();
        log.info("管理端币种缓存失效验证完成，结果: update 后已调用币种缓存失效");
    }

    /**
     * 地区默认币种映射变化会改变国家字典 Value，必须失效国家缓存。
     */
    @Test
    void shouldEvictCountryCacheAfterRegionCurrencyChange() {
        log.info("测试地区币种映射缓存失效，关键输入: 国家 ID 1 默认币种改为 USD");
        IsoCountryMapper countryMapper = mock(IsoCountryMapper.class);
        IsoCurrencyMapper currencyMapper = mock(IsoCurrencyMapper.class);
        IsoDictionaryCacheInvalidator invalidator = mock(IsoDictionaryCacheInvalidator.class);
        IsoCountryDO country = new IsoCountryDO();
        country.setId(1L);
        when(countryMapper.selectById(1L)).thenReturn(country);
        AdminBaseRegionCurrencyApplicationService service = regionCurrencyService(
                countryMapper,
                currencyMapper,
                invalidator
        );

        service.updateRegionCurrency(1L, Map.of("currencyAlpha3Code", "USD"));

        verify(countryMapper).updateById(country);
        verify(invalidator).evictCountries();
        log.info("地区币种映射缓存失效验证完成，结果: 映射更新后已失效国家缓存");
    }

    /** 地区币种入口修改国家状态时必须复用公共 ISO 国家缓存失效链路。 */
    @Test
    void shouldEvictPublicCountryCacheAfterRegionCountryStatusChange() {
        IsoCountryMapper countryMapper = mock(IsoCountryMapper.class);
        IsoDictionaryCacheInvalidator invalidator = mock(IsoDictionaryCacheInvalidator.class);
        IsoCountryDO country = new IsoCountryDO();
        country.setId(1L);
        when(countryMapper.selectById(1L)).thenReturn(country);
        AdminBaseRegionCurrencyApplicationService service = regionCurrencyService(
                countryMapper,
                mock(IsoCurrencyMapper.class),
                invalidator);

        service.updateStatus(1L, Map.of("status", 0));

        verify(countryMapper).updateById(country);
        verify(invalidator).evictCountries();
    }

    /**
     * 创建国家资料应用服务测试实例。
     *
     * @param mapper      国家 Mapper
     * @param invalidator ISO 字典缓存失效器
     * @return 国家资料应用服务
     */
    private AdminBaseCountryApplicationService countryService(
            IsoCountryMapper mapper,
            IsoDictionaryCacheInvalidator invalidator) {
        return new AdminBaseCountryApplicationService(
                mapper,
                invalidator,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class)
        );
    }

    /**
     * 创建币种资料应用服务测试实例。
     *
     * @param mapper      币种 Mapper
     * @param invalidator ISO 字典缓存失效器
     * @return 币种资料应用服务
     */
    private AdminBaseCurrencyApplicationService currencyService(
            IsoCurrencyMapper mapper,
            IsoDictionaryCacheInvalidator invalidator) {
        return new AdminBaseCurrencyApplicationService(
                mapper,
                invalidator,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class)
        );
    }

    /**
     * 创建地区币种应用服务测试实例。
     *
     * @param countryMapper 国家 Mapper
     * @param currencyMapper 币种 Mapper
     * @param invalidator    ISO 字典缓存失效器
     * @return 地区币种应用服务
     */
    private AdminBaseRegionCurrencyApplicationService regionCurrencyService(
            IsoCountryMapper countryMapper,
            IsoCurrencyMapper currencyMapper,
            IsoDictionaryCacheInvalidator invalidator) {
        return new AdminBaseRegionCurrencyApplicationService(
                countryMapper,
                currencyMapper,
                invalidator,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class)
        );
    }

    /** 校验指定写方法使用方法级主库路由和回滚事务。 */
    private void assertMasterTransaction(Class<?> serviceType,
                                         String methodName,
                                         Class<?>... parameterTypes) throws Exception {
        Method method = serviceType.getMethod(methodName, parameterTypes);
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }
}
