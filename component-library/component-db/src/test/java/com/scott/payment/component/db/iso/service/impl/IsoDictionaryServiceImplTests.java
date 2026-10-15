package com.scott.payment.component.db.iso.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyPresentationInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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

    @BeforeAll
    static void initializeIsoTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), IsoCountryDO.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), IsoCurrencyDO.class);
    }

    /** 单个国家代码应通过固定 Hash 的一次 HMGET 命中，避免读取并反序列化全量列表。 */
    @Test
    void shouldReadCountryCodeFromHashWithOneHmget() {
        Fixture fixture = fixture();
        when(fixture.hashOperations().multiGet(
                "acquiring:dev:iso:country:codes", List.of("__READY__", "A3:USA")))
                .thenReturn(List.of("v1", JsonUtils.toJsonString(countryInfo())));

        List<IsoCountryInfo> countries = fixture.service().listCountriesByCodes(null, "USA", null);

        assertThat(countries).containsExactly(countryInfo());
        verify(fixture.hashOperations(), times(1)).multiGet(
                "acquiring:dev:iso:country:codes", List.of("__READY__", "A3:USA"));
        verify(fixture.countryMapper(), never()).selectList(any());
        verify(fixture.valueOperations(), never()).get("acquiring:dev:iso:country:all");
    }

    /** 多个国家代码只有全部映射到同一条权威记录时才算命中。 */
    @Test
    void shouldRequireAllCountryCodeAliasesToResolveToSameRecord() {
        Fixture fixture = fixture();
        IsoCountryInfo canada = new IsoCountryInfo(
                "CA", "CAN", "124", "Canada", "Canada", "加拿大", "NA", "北美洲",
                "CA", "en", "English", "英语", "CAD");
        when(fixture.hashOperations().multiGet(
                "acquiring:dev:iso:country:codes",
                List.of("__READY__", "A2:US", "A3:CAN", "N:840")))
                .thenReturn(List.of(
                        "v1",
                        JsonUtils.toJsonString(countryInfo()),
                        JsonUtils.toJsonString(canada),
                        JsonUtils.toJsonString(countryInfo())
                ));

        assertThat(fixture.service().listCountriesByCodes("US", "CAN", "840")).isEmpty();
        verify(fixture.countryMapper(), never()).selectList(any());
    }

    /** 完整 Hash 中缺少查询字段代表权威未命中，不能重复回源数据库。 */
    @Test
    void shouldTreatMissingCountryFieldInReadyHashAsAuthoritativeEmpty() {
        Fixture fixture = fixture();
        when(fixture.hashOperations().multiGet(
                "acquiring:dev:iso:country:codes", List.of("__READY__", "A3:ZZZ")))
                .thenReturn(java.util.Arrays.asList("v1", null));

        assertThat(fixture.service().listCountriesByCodes(null, "ZZZ", null)).isEmpty();
        verify(fixture.countryMapper(), never()).selectList(any());
    }

    /** Hash 尚未建立时从 SLAVE 全量加载一次，并用一次 HSET 写入完整代码别名。 */
    @Test
    void shouldRebuildCountryCodeHashFromOneDatabaseSnapshot() {
        Fixture fixture = fixture();
        when(fixture.hashOperations().multiGet(anyString(), anyList()))
                .thenReturn(java.util.Arrays.asList(null, null));
        when(fixture.countryMapper().selectList(any())).thenReturn(List.of(countryRow()));

        assertThat(fixture.service().listCountriesByCodes(null, "USA", null))
                .containsExactly(countryInfo());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> aliases = ArgumentCaptor.forClass(Map.class);
        verify(fixture.hashOperations()).putAll(eq("acquiring:dev:iso:country:codes"), aliases.capture());
        assertThat(aliases.getValue())
                .containsEntry("__READY__", "v1")
                .containsEntry("A2:US", JsonUtils.toJsonString(countryInfo()))
                .containsEntry("A3:USA", JsonUtils.toJsonString(countryInfo()))
                .containsEntry("N:840", JsonUtils.toJsonString(countryInfo()));
        verify(fixture.valueOperations()).set(
                eq("acquiring:dev:iso:country:all"),
                eq(JsonUtils.toJsonString(List.of(countryInfo())))
        );
        verify(fixture.countryMapper(), times(1)).selectList(any());
    }

    /** 代码 Hash 缺失但全量快照存在时，必须直接复用快照建立索引，不能再次扫描国家表。 */
    @Test
    void shouldBuildCountryCodeHashFromExistingFullSnapshot() {
        Fixture fixture = fixture();
        when(fixture.hashOperations().multiGet(anyString(), anyList()))
                .thenReturn(java.util.Arrays.asList(null, null));
        when(fixture.valueOperations().get("acquiring:dev:iso:country:all"))
                .thenReturn(JsonUtils.toJsonString(List.of(countryInfo())));

        assertThat(fixture.service().listCountriesByCodes(null, "USA", null))
                .containsExactly(countryInfo());

        verify(fixture.countryMapper(), never()).selectList(any());
        verify(fixture.hashOperations()).putAll(
                eq("acquiring:dev:iso:country:codes"),
                anyMap()
        );
    }

    /** 全量快照仍处于管理端失效门禁时，不得用旧快照重新建立永久国家代码索引。 */
    @Test
    void shouldNotRebuildCountryCodeHashWhileSnapshotInvalidationIsPending() {
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        Fixture fixture = fixture(null, invalidationGuard);
        when(fixture.hashOperations().multiGet(anyString(), anyList()))
                .thenReturn(java.util.Arrays.asList(null, null));
        when(fixture.valueOperations().get("acquiring:dev:iso:country:all"))
                .thenReturn(JsonUtils.toJsonString(List.of(countryInfo())));
        when(invalidationGuard.isPending(PaymentCacheNames.ISO_COUNTRY, "all")).thenReturn(true);

        assertThat(fixture.service().listCountriesByCodes(null, "USA", null))
                .containsExactly(countryInfo());

        verify(fixture.hashOperations(), never()).putAll(anyString(), anyMap());
    }

    /** Redis 不可用时仅执行一次携带全部代码条件的 SLAVE 精确 SQL。 */
    @Test
    void shouldQueryCountryByAllCodesWhenRedisIsUnavailable() {
        Fixture fixture = fixture();
        when(fixture.hashOperations().multiGet(anyString(), anyList()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(fixture.countryMapper().selectList(any())).thenReturn(List.of(countryRow()));

        assertThat(fixture.service().listCountriesByCodes("US", "USA", "840"))
                .containsExactly(countryInfo());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<IsoCountryDO>> query =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(fixture.countryMapper()).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment())
                .contains("alpha2_code", "alpha3_code", "numeric_code", "status", "deleted");
        assertThat(query.getValue().getParamNameValuePairs().values())
                .contains("US", "USA", "840", 1, 0);
    }

    /** 无代码时保留现有全量缓存路径。 */
    @Test
    void shouldUseFullCountrySnapshotWhenNoCodesAreProvided() {
        log.info("验证国家查询无代码时复用 Redis 全量快照");
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:iso:country:all"))
                .thenReturn(JsonUtils.toJsonString(List.of(countryInfo())));

        assertThat(fixture.service().listCountriesByCodes(null, null, null)).containsExactly(countryInfo());
        verify(fixture.countryMapper(), never()).selectList(any());
    }

    /** 币种代码 Hash 使用相同的单次读取、AND 语义与权威未命中规则。 */
    @Test
    void shouldReadCurrencyCodesFromHashAndRejectConflictingAliases() {
        Fixture fixture = fixture();
        IsoCurrencyInfo usd = currencyInfo("USD", "840");
        IsoCurrencyInfo cny = currencyInfo("CNY", "156");
        when(fixture.hashOperations().multiGet(
                "acquiring:dev:iso:currency:codes", List.of("__READY__", "A3:USD", "N:156")))
                .thenReturn(List.of("v1", JsonUtils.toJsonString(usd), JsonUtils.toJsonString(cny)));

        assertThat(fixture.service().listCurrenciesByCodes("USD", "156")).isEmpty();
        verify(fixture.currencyMapper(), never()).selectList(any());
    }

    /** 币种 Hash 缺失时全量重建；Redis 故障时精确 SQL 才承担兜底。 */
    @Test
    void shouldRebuildCurrencyHashAndUseExactSqlOnlyWhenRedisFails() {
        Fixture fixture = fixture();
        IsoCurrencyDO usdRow = currencyRow("USD", "flag:US");
        usdRow.setNumericCode("840");
        when(fixture.hashOperations().multiGet(anyString(), anyList()))
                .thenReturn(java.util.Arrays.asList(null, null));
        when(fixture.currencyMapper().selectList(any())).thenReturn(List.of(usdRow));

        assertThat(fixture.service().listCurrenciesByCodes("USD", null))
                .containsExactly(currencyInfo("USD", "840"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> aliases = ArgumentCaptor.forClass(Map.class);
        verify(fixture.hashOperations()).putAll(eq("acquiring:dev:iso:currency:codes"), aliases.capture());
        assertThat(aliases.getValue())
                .containsEntry("__READY__", "v1")
                .containsEntry("A3:USD", JsonUtils.toJsonString(currencyInfo("USD", "840")))
                .containsEntry("N:840", JsonUtils.toJsonString(currencyInfo("USD", "840")));
        verify(fixture.valueOperations()).set(
                eq("acquiring:dev:iso:currency:all"),
                eq(JsonUtils.toJsonString(List.of(currencyInfo("USD", "840"))))
        );

        Fixture fallbackFixture = fixture();
        when(fallbackFixture.hashOperations().multiGet(anyString(), anyList()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(fallbackFixture.currencyMapper().selectList(any())).thenReturn(List.of(usdRow));
        assertThat(fallbackFixture.service().listCurrenciesByCodes("USD", "840"))
                .containsExactly(currencyInfo("USD", "840"));
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<LambdaQueryWrapper<IsoCurrencyDO>> query =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(fallbackFixture.currencyMapper()).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment())
                .contains("alpha3_code", "numeric_code", "status", "deleted");
    }

    /**
     * 国家字典命中短 Key 后必须直接返回，不能再访问历史 Key 或数据库。
     */
    @Test
    void shouldReadPermanentCountrySnapshotWithoutLegacyLookup() {
        log.info("测试 ISO 国家字典常驻快照，关键输入: acquiring:dev:iso:country:all 命中");
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:iso:country:all"))
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
                org.mockito.ArgumentMatchers.eq("acquiring:dev:iso:country:all"),
                anyString()
        );
        verify(fixture.hashOperations()).putAll(
                eq("acquiring:dev:iso:country:codes"),
                anyMap()
        );
        verify(fixture.valueOperations(), never()).set(
                org.mockito.ArgumentMatchers.eq("acquiring:dev:iso:country:all"),
                anyString(),
                any(java.time.Duration.class)
        );
        verify(fixture.valueOperations(), never()).set(
                org.mockito.ArgumentMatchers.eq("payment:iso:country:all"),
                anyString()
        );
        log.info("ISO 国家字典常驻写入测试完成，结果: 仅写短 Key 且未设置 TTL");
    }

    /** 国家字典业务变更必须同时失效全量快照和固定代码 Hash。 */
    @Test
    void shouldEvictCountrySnapshotAndCodeHashAfterBusinessMutation() {
        log.info("测试 ISO 国家字典显式失效，关键输入: 管理端完成国家资料变更");
        Fixture fixture = fixture();

        fixture.service().evictCountries();

        verify(fixture.redisTemplate()).delete("acquiring:dev:iso:country:all");
        verify(fixture.redisTemplate()).delete("acquiring:dev:iso:country:codes");
        verify(fixture.redisTemplate(), never()).delete("payment:iso:country:all");
        log.info("ISO 国家字典显式失效测试完成，结果: 全量快照和代码 Hash 均被精确删除");
    }

    /** 数据库合法返回零条启用记录时，空数组就是权威快照。 */
    @Test
    void shouldCacheAndReturnEmptySnapshotWhenDatabaseReturnsEmpty() {
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:iso:country:all")).thenReturn(null);
        when(fixture.countryMapper().selectList(any())).thenReturn(List.of());

        List<IsoCountryInfo> countries = fixture.service().listCountries();

        assertThat(countries).isEmpty();
        verify(fixture.valueOperations()).set("acquiring:dev:iso:country:all", "[]");
    }

    /** Redis 中已经缓存的空数组必须直接返回，不能再次访问数据库。 */
    @Test
    void shouldTreatCachedEmptyArrayAsPresentSnapshot() {
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:iso:country:all")).thenReturn("[]");

        assertThat(fixture.service().listCountries()).isEmpty();

        verify(fixture.countryMapper(), never()).selectList(any());
        verify(fixture.valueOperations(), never()).set(anyString(), anyString());
    }

    /** 数据库故障且没有旧快照时必须返回明确服务不可用，不能伪造内置业务数据。 */
    @Test
    void shouldFailWhenDatabaseReadFailsWithoutCachedSnapshot() {
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:iso:country:all")).thenReturn(null);
        when(fixture.countryMapper().selectList(any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> fixture.service().listCountries())
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.NETWORK_BUSY.getCode());
        verify(fixture.valueOperations(), never()).set(anyString(), anyString());
    }

    /**
     * 币种字典必须使用统一短 Key，命中时不访问历史命名空间。
     */
    @Test
    void shouldReadPermanentCurrencySnapshotFromConciseKey() {
        log.info("测试 ISO 币种字典常驻快照，关键输入: acquiring:dev:iso:currency:all 命中");
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
        when(fixture.valueOperations().get("acquiring:dev:iso:currency:all"))
                .thenReturn(JsonUtils.toJsonString(List.of(currency)));

        assertThat(fixture.service().listCurrencies()).containsExactly(currency);
        verify(fixture.valueOperations(), never()).get("payment:iso:currency:all");
        log.info("ISO 币种字典常驻快照测试完成，结果: 仅访问统一短 Key");
    }

    /** 币种展示快照必须使用独立版本键，不能复用金额计算字典缓存。 */
    @Test
    void shouldReadCurrencyPresentationSnapshotFromVersionedKey() {
        Fixture fixture = fixture();
        IsoCurrencyPresentationInfo presentation = new IsoCurrencyPresentationInfo(
                "USD", "美元", "US Dollar", "$", "flag:US");
        when(fixture.valueOperations().get("acquiring:dev:iso:currency:presentation-v1"))
                .thenReturn(JsonUtils.toJsonString(List.of(presentation)));

        assertThat(fixture.service().listCurrencyPresentations()).containsExactly(presentation);
        verify(fixture.currencyMapper(), never()).selectList(any());
        verify(fixture.valueOperations(), never()).get("acquiring:dev:iso:currency:all");
    }

    /** 数据库展示记录应写入版本化永久缓存，非法历史图标键必须降级为空。 */
    @Test
    void shouldWriteSafeCurrencyPresentationSnapshotAfterDatabaseLoad() {
        Fixture fixture = fixture();
        IsoCurrencyDO usd = currencyRow("USD", "flag:US");
        IsoCurrencyDO cny = currencyRow("CNY", "https://cdn.example.com/cny.svg");
        when(fixture.valueOperations().get("acquiring:dev:iso:currency:presentation-v1"))
                .thenReturn(null);
        when(fixture.currencyMapper().selectList(any())).thenReturn(List.of(usd, cny));

        List<IsoCurrencyPresentationInfo> presentations = fixture.service().listCurrencyPresentations();

        assertThat(presentations).extracting(IsoCurrencyPresentationInfo::alphabeticCode)
                .containsExactly("CNY", "USD");
        assertThat(presentations.get(0).iconKey()).isNull();
        assertThat(presentations.get(1).iconKey()).isEqualTo("flag:US");
        verify(fixture.valueOperations()).set(
                org.mockito.ArgumentMatchers.eq("acquiring:dev:iso:currency:presentation-v1"),
                anyString()
        );
    }

    /** 币种维护后必须同时失效全量快照、固定代码 Hash 和展示快照。 */
    @Test
    void shouldEvictCurrencyCodeHashAndSnapshotsTogether() {
        Fixture fixture = fixture();

        fixture.service().evictCurrencies();

        verify(fixture.redisTemplate()).delete("acquiring:dev:iso:currency:all");
        verify(fixture.redisTemplate()).delete("acquiring:dev:iso:currency:codes");
        verify(fixture.redisTemplate()).delete("acquiring:dev:iso:currency:presentation-v1");
    }

    /** 管理端事务环境必须为每个固定国家缓存键登记独立的可靠失效事件。 */
    @Test
    void shouldPrepareManagedInvalidationForEveryCountryCacheKey() {
        ManagedCacheInvalidationCoordinator coordinator = mock(ManagedCacheInvalidationCoordinator.class);
        Fixture fixture = fixture(coordinator);

        fixture.service().evictCountries();

        verify(coordinator).prepare(PaymentCacheNames.ISO_COUNTRY, "all");
        verify(coordinator).prepare(PaymentCacheNames.ISO_COUNTRY, "codes");
        verify(fixture.redisTemplate(), never()).delete(anyString());
    }

    /** 币种变更必须让全量、代码和展示三个永久读模型一起越过从库可见性门禁。 */
    @Test
    void shouldPrepareManagedInvalidationForEveryCurrencyCacheKey() {
        ManagedCacheInvalidationCoordinator coordinator = mock(ManagedCacheInvalidationCoordinator.class);
        Fixture fixture = fixture(coordinator);

        fixture.service().evictCurrencies();

        verify(coordinator).prepare(PaymentCacheNames.ISO_CURRENCY, "all");
        verify(coordinator).prepare(PaymentCacheNames.ISO_CURRENCY, "codes");
        verify(coordinator).prepare(PaymentCacheNames.ISO_CURRENCY, "presentation-v1");
        verify(fixture.redisTemplate(), never()).delete(anyString());
    }

    /**
     * 创建使用 dev 环境精简 Key 的 ISO 字典测试夹具。
     *
     * @return Mapper、Redis 模板和待测服务
     */
    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        return fixture(null);
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(ManagedCacheInvalidationCoordinator invalidationCoordinator) {
        return fixture(invalidationCoordinator, null);
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(ManagedCacheInvalidationCoordinator invalidationCoordinator,
                            CacheInvalidationGuard invalidationGuard) {
        IsoCountryMapper countryMapper = mock(IsoCountryMapper.class);
        IsoCurrencyMapper currencyMapper = mock(IsoCurrencyMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        PaymentRedisKeyResolver keyResolver = mock(PaymentRedisKeyResolver.class);
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        ObjectProvider<PaymentRedisKeyResolver> keyResolverProvider = mock(ObjectProvider.class);
        ObjectProvider<CacheInvalidationGuard> invalidationGuardProvider = mock(ObjectProvider.class);
        ObjectProvider<ManagedCacheInvalidationCoordinator> invalidationCoordinatorProvider =
                mock(ObjectProvider.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(keyResolverProvider.getIfAvailable()).thenReturn(keyResolver);
        when(invalidationGuardProvider.getIfAvailable()).thenReturn(invalidationGuard);
        when(invalidationCoordinatorProvider.getIfAvailable()).thenReturn(invalidationCoordinator);
        when(keyResolver.businessKey("iso", "country", "all"))
                .thenReturn("acquiring:dev:iso:country:all");
        when(keyResolver.businessKey("iso", "country", "codes"))
                .thenReturn("acquiring:dev:iso:country:codes");
        when(keyResolver.businessKey("iso", "currency", "all"))
                .thenReturn("acquiring:dev:iso:currency:all");
        when(keyResolver.businessKey("iso", "currency", "codes"))
                .thenReturn("acquiring:dev:iso:currency:codes");
        when(keyResolver.businessKey("iso", "currency", "presentation-v1"))
                .thenReturn("acquiring:dev:iso:currency:presentation-v1");
        IsoDictionaryServiceImpl service = new IsoDictionaryServiceImpl(
                countryMapper,
                currencyMapper,
                redisProvider,
                keyResolverProvider,
                invalidationGuardProvider,
                invalidationCoordinatorProvider
        );
        return new Fixture(
                countryMapper,
                currencyMapper,
                redisTemplate,
                valueOperations,
                hashOperations,
                service
        );
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

    private IsoCurrencyDO currencyRow(String alphabeticCode, String iconKey) {
        IsoCurrencyDO row = new IsoCurrencyDO();
        row.setAlpha3Code(alphabeticCode);
        row.setNumericCode("000");
        row.setEnglishName(alphabeticCode + " currency");
        row.setChineseName(alphabeticCode + " 币种");
        row.setFractionDigits(2);
        row.setMinorUnitMultiplier(100L);
        row.setMinimumAmount(new BigDecimal("0.01"));
        row.setCurrencySymbol(alphabeticCode);
        row.setIconKey(iconKey);
        return row;
    }

    private IsoCurrencyInfo currencyInfo(String alphabeticCode, String numericCode) {
        return new IsoCurrencyInfo(
                alphabeticCode,
                numericCode,
                alphabeticCode + " currency",
                alphabeticCode + " 币种",
                2,
                100,
                new BigDecimal("0.01"),
                alphabeticCode
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
                           IsoCurrencyMapper currencyMapper,
                           StringRedisTemplate redisTemplate,
                           ValueOperations<String, String> valueOperations,
                           HashOperations<String, Object, Object> hashOperations,
                           IsoDictionaryServiceImpl service) {
    }
}
