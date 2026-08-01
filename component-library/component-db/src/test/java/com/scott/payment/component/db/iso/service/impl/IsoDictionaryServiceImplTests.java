package com.scott.payment.component.db.iso.service.impl;

import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoDictionaryServiceImplTests
 * @date : 2026-07-30 21:45
 * @email : scott_x@163.com
 * @description : ISO 国家与币种常驻缓存短 Key、数据库兜底边界和管理端精确失效行为单元测试
 * @status : update
 */
@Slf4j
class IsoDictionaryServiceImplTests {

    /**
     * 国家字典命中短 Key 后必须直接返回，不能再访问历史 Key 或数据库。
     */
    @Test
    void shouldReadPermanentCountrySnapshotWithoutLegacyLookup() {
        log.info("测试 ISO 国家字典常驻快照，关键输入: acquiring:dev:iso:country 命中");
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:iso:country"))
                .thenReturn(JsonUtils.toJsonString(List.of(countryInfo())));

        List<IsoCountryInfo> countries = fixture.service().listCountries();

        assertThat(countries).containsExactly(countryInfo());
        verify(fixture.countryMapper(), never()).selectList(any());
        verify(fixture.valueOperations(), never()).get("payment:iso:country:all");
        verify(fixture.valueOperations(), never()).set(anyString(), anyString());
        log.info("ISO 国家字典常驻快照测试完成，结果: 仅访问短 Key 且未回源数据库");
    }

    /**
     * 缓存未命中时必须查询数据库，并且只写入不带 TTL 的短 Key。
     */
    @Test
    void shouldWritePermanentCountrySnapshotAfterDatabaseLoad() {
        log.info("测试 ISO 国家字典常驻写入，关键输入: 短 Key 未命中、数据库返回 1 条记录");
        Fixture fixture = fixture();
        when(fixture.valueOperations().get(anyString())).thenReturn(null);
        when(fixture.countryMapper().selectList(any())).thenReturn(List.of(countryRow()));

        List<IsoCountryInfo> countries = fixture.service().listCountries();

        assertThat(countries).containsExactly(countryInfo());
        verify(fixture.valueOperations()).set(
                org.mockito.ArgumentMatchers.eq("acquiring:dev:iso:country"),
                anyString()
        );
        verify(fixture.valueOperations(), never()).set(
                org.mockito.ArgumentMatchers.eq("acquiring:dev:iso:country"),
                anyString(),
                any(java.time.Duration.class)
        );
        verify(fixture.valueOperations(), never()).set(
                org.mockito.ArgumentMatchers.eq("payment:iso:country:all"),
                anyString()
        );
        log.info("ISO 国家字典常驻写入测试完成，结果: 仅写短 Key 且未设置 TTL");
    }

    /**
     * 国家字典业务变更必须只精确删除当前短 Key。
     */
    @Test
    void shouldEvictOnlyCurrentCountryKeyAfterBusinessMutation() {
        log.info("测试 ISO 国家字典显式失效，关键输入: 管理端完成国家资料变更");
        Fixture fixture = fixture();

        fixture.service().evictCountries();

        verify(fixture.redisTemplate()).delete("acquiring:dev:iso:country");
        verify(fixture.redisTemplate(), never()).delete("payment:iso:country:all");
        log.info("ISO 国家字典显式失效测试完成，结果: 仅当前短 Key 被精确删除");
    }

    /**
     * 数据库返回空结果时内置字典只用于当前请求，不能写入常驻缓存。
     */
    @Test
    void shouldNotCacheFallbackWhenDatabaseReturnsEmpty() {
        log.info("测试 ISO 字典空库兜底边界，关键输入: Redis 未命中、数据库返回空列表");
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:iso:country")).thenReturn(null);
        when(fixture.countryMapper().selectList(any())).thenReturn(List.of());

        List<IsoCountryInfo> countries = fixture.service().listCountries();

        assertThat(countries).isNotEmpty();
        verify(fixture.valueOperations(), never()).set(anyString(), anyString());
        log.info("ISO 字典空库兜底测试完成，结果: 返回内置数据但未污染常驻缓存");
    }

    /**
     * 数据库故障时内置字典只用于当前请求，不能把故障期间数据固化到 Redis。
     */
    @Test
    void shouldNotCacheFallbackWhenDatabaseReadFails() {
        log.info("测试 ISO 字典数据库故障兜底，关键输入: 国家 Mapper 抛出数据访问异常");
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:iso:country")).thenReturn(null);
        when(fixture.countryMapper().selectList(any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        List<IsoCountryInfo> countries = fixture.service().listCountries();

        assertThat(countries).isNotEmpty();
        verify(fixture.valueOperations(), never()).set(anyString(), anyString());
        log.info("ISO 字典数据库故障兜底测试完成，结果: 返回内置数据但未写常驻缓存");
    }

    /**
     * 币种字典必须使用统一短 Key，命中时不访问历史命名空间。
     */
    @Test
    void shouldReadPermanentCurrencySnapshotFromConciseKey() {
        log.info("测试 ISO 币种字典常驻快照，关键输入: acquiring:dev:iso:currency 命中");
        Fixture fixture = fixture();
        IsoCurrencyInfo currency = new IsoCurrencyInfo(
                "USD",
                "840",
                "US Dollar",
                "美元",
                2,
                100,
                new BigDecimal("0.01"),
                "$"
        );
        when(fixture.valueOperations().get("acquiring:dev:iso:currency"))
                .thenReturn(JsonUtils.toJsonString(List.of(currency)));

        assertThat(fixture.service().listCurrencies()).containsExactly(currency);
        verify(fixture.valueOperations(), never()).get("payment:iso:currency:all");
        log.info("ISO 币种字典常驻快照测试完成，结果: 仅访问统一短 Key");
    }

    /**
     * 创建使用 dev 环境精简 Key 的 ISO 字典测试夹具。
     *
     * @return Mapper、Redis 模板和待测服务
     */
    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        IsoCountryMapper countryMapper = mock(IsoCountryMapper.class);
        IsoCurrencyMapper currencyMapper = mock(IsoCurrencyMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        PaymentRedisKeyResolver keyResolver = mock(PaymentRedisKeyResolver.class);
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        ObjectProvider<PaymentRedisKeyResolver> keyResolverProvider = mock(ObjectProvider.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(keyResolverProvider.getIfAvailable()).thenReturn(keyResolver);
        when(keyResolver.businessKey("iso", "country"))
                .thenReturn("acquiring:dev:iso:country");
        when(keyResolver.businessKey("iso", "currency"))
                .thenReturn("acquiring:dev:iso:currency");
        IsoDictionaryServiceImpl service = new IsoDictionaryServiceImpl(
                countryMapper,
                currencyMapper,
                redisProvider,
                keyResolverProvider
        );
        return new Fixture(countryMapper, redisTemplate, valueOperations, service);
    }

    /**
     * 构造单条启用国家数据库记录。
     *
     * @return 美国 ISO 国家记录
     */
    private IsoCountryDO countryRow() {
        IsoCountryDO row = new IsoCountryDO();
        row.setAlpha2Code("US");
        row.setAlpha3Code("USA");
        row.setNumericCode("840");
        row.setEnglishName("United States of America");
        row.setShortEnglishName("United States");
        row.setChineseName("美国");
        row.setContinentCode("NA");
        row.setContinentName("北美洲");
        row.setFlagEmoji("US");
        row.setPrimaryLanguageCode("en");
        row.setPrimaryLanguageEnglish("English");
        row.setPrimaryLanguageChinese("英语");
        row.setCurrencyAlpha3Code("USD");
        return row;
    }

    /**
     * 构造与数据库记录字段一致的 ISO 国家信息。
     *
     * @return 美国 ISO 国家信息
     */
    private IsoCountryInfo countryInfo() {
        return new IsoCountryInfo(
                "US",
                "USA",
                "840",
                "United States of America",
                "United States",
                "美国",
                "NA",
                "北美洲",
                "US",
                "en",
                "English",
                "英语",
                "USD"
        );
    }

    /**
     * ISO 字典测试依赖集合。
     *
     * @param countryMapper   国家 Mapper
     * @param redisTemplate  Redis 字符串模板
     * @param valueOperations Redis String 操作接口
     * @param service         待测服务
     */
    private record Fixture(IsoCountryMapper countryMapper,
                           StringRedisTemplate redisTemplate,
                           ValueOperations<String, String> valueOperations,
                           IsoDictionaryServiceImpl service) {
    }
}
