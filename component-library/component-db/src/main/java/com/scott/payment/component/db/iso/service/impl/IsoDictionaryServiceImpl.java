package com.scott.payment.component.db.iso.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyIconKey;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyPresentationInfo;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.db.iso.service.IsoDictionaryCacheInvalidator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoDictionaryServiceImpl
 * @date : 2026-06-03 14:35
 * @email : scott_x@163.com
 * @description : ISO 国家地区与币种基础字典公共查询服务实现，提供数据库权威数据的常驻 Redis 读模型
 * @status : update
 */
@Slf4j
@Service
public class IsoDictionaryServiceImpl implements IsoDictionaryService, IsoDictionaryCacheInvalidator {

    /**
     * 启用状态值。
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 未删除状态值。
     */
    private static final int NOT_DELETED = 0;

    /**
     * ISO 字典新 Key 的业务域。
     */
    private static final String ISO_CACHE_DOMAIN = "iso";

    /**
     * ISO 国家地区新 Key 的业务用途。
     */
    private static final String COUNTRY_CACHE_BUSINESS = "country";

    /**
     * ISO 币种新 Key 的业务用途。
     */
    private static final String CURRENCY_CACHE_BUSINESS = "currency";

    /** 币种展示快照版本；升级展示结构时使用新业务键，避免反序列化旧值。 */
    private static final String CURRENCY_PRESENTATION_CACHE_BUSINESS_KEY = "presentation-v1";

    /** 受管 ISO 快照的固定业务键。 */
    private static final String SNAPSHOT_BUSINESS_KEY = "all";

    /** 按标准代码精确查询的固定 Redis Hash 业务键。 */
    private static final String CODE_INDEX_BUSINESS_KEY = "codes";

    /** 代码 Hash 完整性标记；与全部别名在同一个 HSET 命令中写入。 */
    private static final String CODE_INDEX_READY_FIELD = "__READY__";

    /** 当前代码 Hash 载荷版本。 */
    private static final String CODE_INDEX_VERSION = "v1";

    private static final String ALPHA2_FIELD_PREFIX = "A2:";
    private static final String ALPHA3_FIELD_PREFIX = "A3:";
    private static final String NUMERIC_FIELD_PREFIX = "N:";

    /**
     * 国家地区 Mapper，用于读取 base_iso_country。
     */
    private final IsoCountryMapper countryMapper;

    /**
     * 币种 Mapper，用于读取 base_iso_currency。
     */
    private final IsoCurrencyMapper currencyMapper;

    /**
     * Redis 字符串模板。Redis 不存在时保持为空，不影响 DB 查询。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 统一 Redis Key 解析器。组件未引入 Redis 实现时允许为空，此时直接回源数据库。
     */
    private final PaymentRedisKeyResolver keyResolver;

    /** 永久缓存失效门禁；未启用 Redis 组件时允许为空并直接回源数据库。 */
    private final CacheInvalidationGuard invalidationGuard;

    /** 事务缓存可靠失效协调器；独立使用 component-db 时允许为空。 */
    private final ManagedCacheInvalidationCoordinator invalidationCoordinator;

    /**
     * 创建 ISO 字典服务实现。
     *
     * @param countryMapper               国家地区 Mapper
     * @param currencyMapper              币种 Mapper
     * @param stringRedisTemplateProvider Redis 模板提供器
     * @param keyResolverProvider         统一 Redis Key 解析器提供器
     * @param invalidationGuardProvider   永久缓存失效门禁提供器
     * @param invalidationCoordinatorProvider 事务缓存可靠失效协调器提供器
     */
    public IsoDictionaryServiceImpl(IsoCountryMapper countryMapper,
                                    IsoCurrencyMapper currencyMapper,
                                    ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                    ObjectProvider<PaymentRedisKeyResolver> keyResolverProvider,
                                    ObjectProvider<CacheInvalidationGuard> invalidationGuardProvider,
                                    ObjectProvider<ManagedCacheInvalidationCoordinator>
                                            invalidationCoordinatorProvider) {
        this.countryMapper = countryMapper;
        this.currencyMapper = currencyMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.keyResolver = keyResolverProvider.getIfAvailable();
        this.invalidationGuard = invalidationGuardProvider.getIfAvailable();
        this.invalidationCoordinator = invalidationCoordinatorProvider.getIfAvailable();
    }

    /**
     * 查询全部启用国家地区。
     *
     * <p>优先读取不设置物理 TTL 的全量常驻快照；已缓存的空数组同样视为权威命中。
     * 缓存缺失或 Redis 不可用时，从 {@code SLAVE} 数据源查询数据库权威数据。回源成功后，
     * 分别在对应失效门禁允许且 Redis 可用时，尝试写入全量快照并使用同一份数据重建国家代码
     * Hash，避免两个读模型来源不一致。数据库读取失败时不使用内置国家数据兜底。</p>
     *
     * @return 启用国家地区列表
     * @throws ServiceException 数据库读取失败且没有可用缓存快照时抛出
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCountryInfo> listCountries() {
        // 全量查询复用常驻快照；回源成功后以同一份数据维护列表快照和代码索引，
        // 两类写入分别受可靠失效门禁保护，避免在主从复制期间固化旧数据。
        return loadFromCache(
                PaymentCacheNames.ISO_COUNTRY,
                newCacheKey(COUNTRY_CACHE_BUSINESS),
                SNAPSHOT_BUSINESS_KEY,
                new TypeReference<List<IsoCountryInfo>>() {
                },
                this::loadCountriesFromDatabase,
                this::writeCountryCodeIndex
        );
    }

    /**
     * 从固定国家代码 Hash 按已提供的 ISO 代码执行精确组合查询。
     *
     * <p>所有非空条件通过一次 HMGET 读取，并且必须解析为同一条国家记录。完整 Hash 中字段
     * 不存在属于权威空结果；Hash 尚未建立时从 SLAVE 全量加载一次并写入所有代码别名；只有
     * Redis 不可用时才执行一次携带全部条件的 SLAVE 精确 SQL。固定 Hash 不接受商户输入作为
     * Redis Key，避免恶意请求造成无界 Key 增长。</p>
     *
     * @param alpha2 ISO 3166-1 两位代码，可为空
     * @param alpha3 ISO 3166-1 三位代码，可为空
     * @param numeric ISO 3166-1 三位数字字符串，可为空
     * @return 命中的启用国家，未命中时返回空列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCountryInfo> listCountriesByCodes(String alpha2, String alpha3, String numeric) {
        if (alpha2 == null && alpha3 == null && numeric == null) {
            return listCountries();
        }
        String cacheKey = newCacheKey(COUNTRY_CACHE_BUSINESS, CODE_INDEX_BUSINESS_KEY);
        List<Object> fields = countryCodeFields(alpha2, alpha3, numeric);
        CodeCacheReadResult<IsoCountryInfo> cacheRead = readCodeCache(
                cacheKey, fields, IsoCountryInfo.class, "COUNTRY");
        if (cacheRead.status() == CodeCacheReadStatus.PRESENT) {
            log.debug("event: ISO_CODE_CACHE_HIT dictionary: COUNTRY criteriaCount: {} matched: {}",
                    fields.size() - 1, !cacheRead.values().isEmpty());
            return cacheRead.values();
        }
        if (cacheRead.status() == CodeCacheReadStatus.UNAVAILABLE) {
            log.debug("event: ISO_CODE_CACHE_MISS dictionary: COUNTRY reason: REDIS_UNAVAILABLE");
            return loadCountriesByCodesFromDatabase(alpha2, alpha3, numeric);
        }

        log.debug("event: ISO_CODE_CACHE_MISS dictionary: COUNTRY reason: INDEX_NOT_READY");
        // 代码索引缺失时优先复用全量快照；只有两个缓存都不存在时才扫描一次从库。
        List<IsoCountryInfo> countries = loadCountriesForCodeIndex();
        return filterCountriesByCodes(countries, alpha2, alpha3, numeric);
    }

    /**
     * 按关键字查询国家地区。
     *
     * @param keyword 查询关键字，空值时返回全部启用国家地区
     * @return 命中的国家地区列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCountryInfo> searchCountries(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return listCountries();
        }
        String normalizedKeyword = normalize(keyword);
        return listCountries()
                .stream()
                .filter(country -> countryMatches(country, normalizedKeyword))
                .toList();
    }

    /**
     * 精确识别国家地区。
     *
     * @param value 国家地区代码或名称
     * @return 命中的国家地区
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public Optional<IsoCountryInfo> getCountry(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        String normalizedValue = normalize(value);
        return listCountries()
                .stream()
                .filter(country -> exactCountryMatches(country, normalizedValue))
                .findFirst();
    }

    /**
     * 根据七大洲代码查询国家地区。
     *
     * @param continentCode 七大洲代码：AS/EU/AF/NA/SA/OC/AN
     * @return 指定大洲下的国家地区列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCountryInfo> listCountriesByContinent(String continentCode) {
        if (!StringUtils.hasText(continentCode)) {
            return listCountries();
        }
        String normalizedContinentCode = normalize(continentCode);
        return listCountries()
                .stream()
                .filter(country -> normalizedContinentCode.equals(normalize(country.continentCode())))
                .toList();
    }

    /**
     * 根据默认币种查询国家地区。
     *
     * @param currencyAlpha3Code ISO 4217 三位字母币种代码
     * @return 默认使用该币种的国家地区列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCountryInfo> listCountriesByCurrency(String currencyAlpha3Code) {
        if (!StringUtils.hasText(currencyAlpha3Code)) {
            return listCountries();
        }
        String normalizedCurrencyCode = normalize(currencyAlpha3Code);
        return listCountries()
                .stream()
                .filter(country -> normalizedCurrencyCode.equals(normalize(country.currencyAlpha3Code())))
                .toList();
    }

    /**
     * 查询全部启用币种。
     *
     * @return 启用币种列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCurrencyInfo> listCurrencies() {
        return loadFromCache(
                PaymentCacheNames.ISO_CURRENCY,
                newCacheKey(CURRENCY_CACHE_BUSINESS),
                SNAPSHOT_BUSINESS_KEY,
                new TypeReference<List<IsoCurrencyInfo>>() {
                },
                this::loadCurrenciesFromDatabase,
                this::writeCurrencyCodeIndex
        );
    }

    /**
     * 从固定币种代码 Hash 按已提供的 ISO 代码执行精确组合查询。
     *
     * <p>字母代码和数字代码通过一次 HMGET 读取，多个条件必须解析为同一条币种记录。
     * Hash 缺失时从 SLAVE 全量重建；Redis 不可用时使用带 AND 条件的 SLAVE 精确 SQL。</p>
     *
     * @param alphabeticCode ISO 4217 三位字母代码，可为空
     * @param numericCode ISO 4217 三位数字字符串，可为空
     * @return 命中的启用币种，未命中时返回空列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCurrencyInfo> listCurrenciesByCodes(String alphabeticCode, String numericCode) {
        if (alphabeticCode == null && numericCode == null) {
            return listCurrencies();
        }
        String cacheKey = newCacheKey(CURRENCY_CACHE_BUSINESS, CODE_INDEX_BUSINESS_KEY);
        List<Object> fields = currencyCodeFields(alphabeticCode, numericCode);
        CodeCacheReadResult<IsoCurrencyInfo> cacheRead = readCodeCache(
                cacheKey, fields, IsoCurrencyInfo.class, "CURRENCY");
        if (cacheRead.status() == CodeCacheReadStatus.PRESENT) {
            log.debug("event: ISO_CODE_CACHE_HIT dictionary: CURRENCY criteriaCount: {} matched: {}",
                    fields.size() - 1, !cacheRead.values().isEmpty());
            return cacheRead.values();
        }
        if (cacheRead.status() == CodeCacheReadStatus.UNAVAILABLE) {
            log.debug("event: ISO_CODE_CACHE_MISS dictionary: CURRENCY reason: REDIS_UNAVAILABLE");
            return loadCurrenciesByCodesFromDatabase(alphabeticCode, numericCode);
        }

        log.debug("event: ISO_CODE_CACHE_MISS dictionary: CURRENCY reason: INDEX_NOT_READY");
        // 代码索引缺失时优先复用全量快照；只有两个缓存都不存在时才扫描一次从库。
        List<IsoCurrencyInfo> currencies = loadCurrenciesForCodeIndex();
        return filterCurrenciesByCodes(currencies, alphabeticCode, numericCode);
    }

    /**
     * 查询启用币种的界面展示快照，不把 Logo 元数据混入金额计算模型。
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCurrencyPresentationInfo> listCurrencyPresentations() {
        return loadFromCache(
                PaymentCacheNames.ISO_CURRENCY,
                newCacheKey(CURRENCY_CACHE_BUSINESS, CURRENCY_PRESENTATION_CACHE_BUSINESS_KEY),
                CURRENCY_PRESENTATION_CACHE_BUSINESS_KEY,
                new TypeReference<List<IsoCurrencyPresentationInfo>>() {
                },
                this::loadCurrencyPresentationsFromDatabase
        );
    }

    /**
     * 按关键字查询币种。
     *
     * @param keyword 查询关键字，空值时返回全部启用币种
     * @return 命中的币种列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<IsoCurrencyInfo> searchCurrencies(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return listCurrencies();
        }
        String normalizedKeyword = normalize(keyword);
        return listCurrencies()
                .stream()
                .filter(currency -> currencyMatches(currency, normalizedKeyword))
                .toList();
    }

    /**
     * 精确识别币种。
     *
     * @param value 币种代码或名称
     * @return 命中的币种信息
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public Optional<IsoCurrencyInfo> getCurrency(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        String normalizedValue = normalize(value);
        return listCurrencies()
                .stream()
                .filter(currency -> exactCurrencyMatches(currency, normalizedValue))
                .findFirst();
    }

    /**
     * 校验金额小数位是否符合币种默认辅币位。
     *
     * @param amount        交易金额，禁止使用 double/float
     * @param currencyValue 币种代码或名称
     * @return true 表示金额小数位合法
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public boolean isCurrencyFractionValid(BigDecimal amount, String currencyValue) {
        return getCurrency(currencyValue)
                .map(currency -> IsoCurrencyResolver.isValidFraction(amount, currency))
                .orElse(false);
    }

    /**
     * 将主币单位金额转换为最小辅币单位。
     *
     * @param amount        主币单位金额
     * @param currencyValue 币种代码或名称
     * @return 最小辅币单位金额
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public long toMinorUnit(BigDecimal amount, String currencyValue) {
        IsoCurrencyInfo currency = getCurrency(currencyValue)
                .orElseThrow(() -> new IllegalArgumentException("currency can not be resolved"));
        return IsoCurrencyResolver.toMinorUnit(amount, currency);
    }

    /**
     * 删除国家地区常驻全量快照和代码 Hash，使下一次读取从数据库重建权威数据。
     *
     * <p>管理端国家或地区关联数据提交成功后调用。Redis 删除失败会向上抛出，
     * 防止业务变更后继续静默使用永久旧值。</p>
     */
    @Override
    public void evictCountries() {
        invalidate(
                PaymentCacheNames.ISO_COUNTRY,
                SNAPSHOT_BUSINESS_KEY,
                newCacheKey(COUNTRY_CACHE_BUSINESS)
        );
        invalidate(
                PaymentCacheNames.ISO_COUNTRY,
                CODE_INDEX_BUSINESS_KEY,
                newCacheKey(COUNTRY_CACHE_BUSINESS, CODE_INDEX_BUSINESS_KEY)
        );
    }

    /**
     * 删除币种常驻全量快照、代码 Hash 和展示快照，使下一次读取从数据库重建权威数据。
     *
     * <p>管理端币种或地区币种关联数据提交成功后调用。Redis 删除失败会向上抛出，
     * 防止业务变更后继续静默使用永久旧值。</p>
     */
    @Override
    public void evictCurrencies() {
        invalidate(
                PaymentCacheNames.ISO_CURRENCY,
                SNAPSHOT_BUSINESS_KEY,
                newCacheKey(CURRENCY_CACHE_BUSINESS)
        );
        invalidate(
                PaymentCacheNames.ISO_CURRENCY,
                CODE_INDEX_BUSINESS_KEY,
                newCacheKey(CURRENCY_CACHE_BUSINESS, CODE_INDEX_BUSINESS_KEY)
        );
        invalidate(
                PaymentCacheNames.ISO_CURRENCY,
                CURRENCY_PRESENTATION_CACHE_BUSINESS_KEY,
                newCacheKey(CURRENCY_CACHE_BUSINESS, CURRENCY_PRESENTATION_CACHE_BUSINESS_KEY)
        );
    }

    /**
     * 国家代码索引缺失时优先复用已有全量快照；全量快照也缺失时才读取一次从库。
     *
     * @return 用于重建代码索引和完成本次精确查询的国家列表
     */
    private List<IsoCountryInfo> loadCountriesForCodeIndex() {
        String snapshotKey = newCacheKey(COUNTRY_CACHE_BUSINESS);
        CacheReadResult<IsoCountryInfo> snapshotRead = readCache(
                snapshotKey,
                new TypeReference<List<IsoCountryInfo>>() {
                }
        );
        List<IsoCountryInfo> countries;
        if (snapshotRead.status() == CacheReadStatus.PRESENT) {
            countries = snapshotRead.values();
        } else {
            countries = loadDatabaseValues(this::loadCountriesFromDatabase);
            writeCountrySnapshot(countries, snapshotKey);
        }
        writeCountryCodeIndex(countries);
        return countries;
    }

    /**
     * 币种代码索引缺失时优先复用已有全量快照；全量快照也缺失时才读取一次从库。
     *
     * @return 用于重建代码索引和完成本次精确查询的币种列表
     */
    private List<IsoCurrencyInfo> loadCurrenciesForCodeIndex() {
        String snapshotKey = newCacheKey(CURRENCY_CACHE_BUSINESS);
        CacheReadResult<IsoCurrencyInfo> snapshotRead = readCache(
                snapshotKey,
                new TypeReference<List<IsoCurrencyInfo>>() {
                }
        );
        List<IsoCurrencyInfo> currencies;
        if (snapshotRead.status() == CacheReadStatus.PRESENT) {
            currencies = snapshotRead.values();
        } else {
            currencies = loadDatabaseValues(this::loadCurrenciesFromDatabase);
            writeCurrencySnapshot(currencies, snapshotKey);
        }
        writeCurrencyCodeIndex(currencies);
        return currencies;
    }

    /** 写入国家全量快照；管理端变更尚未复制完成时跳过永久缓存写入。 */
    private void writeCountrySnapshot(List<IsoCountryInfo> countries, String snapshotKey) {
        if (isCacheWriteAllowed(PaymentCacheNames.ISO_COUNTRY, SNAPSHOT_BUSINESS_KEY)) {
            writeCache(snapshotKey, countries);
        }
    }

    /** 写入币种全量快照；管理端变更尚未复制完成时跳过永久缓存写入。 */
    private void writeCurrencySnapshot(List<IsoCurrencyInfo> currencies, String snapshotKey) {
        if (isCacheWriteAllowed(PaymentCacheNames.ISO_CURRENCY, SNAPSHOT_BUSINESS_KEY)) {
            writeCache(snapshotKey, currencies);
        }
    }

    /** 使用完整国家快照重建固定代码 Hash。 */
    private void writeCountryCodeIndex(List<IsoCountryInfo> countries) {
        if (!isCodeIndexWriteAllowed(PaymentCacheNames.ISO_COUNTRY)) {
            return;
        }
        Map<String, String> aliases = countryAliases(countries);
        if (writeCodeCache(
                newCacheKey(COUNTRY_CACHE_BUSINESS, CODE_INDEX_BUSINESS_KEY),
                aliases,
                "COUNTRY")) {
            log.debug("event: ISO_CODE_CACHE_REBUILT dictionary: COUNTRY recordCount: {} aliasCount: {}",
                    countries.size(), aliases.size() - 1);
        }
    }

    /** 使用完整币种快照重建固定代码 Hash。 */
    private void writeCurrencyCodeIndex(List<IsoCurrencyInfo> currencies) {
        if (!isCodeIndexWriteAllowed(PaymentCacheNames.ISO_CURRENCY)) {
            return;
        }
        Map<String, String> aliases = currencyAliases(currencies);
        if (writeCodeCache(
                newCacheKey(CURRENCY_CACHE_BUSINESS, CODE_INDEX_BUSINESS_KEY),
                aliases,
                "CURRENCY")) {
            log.debug("event: ISO_CODE_CACHE_REBUILT dictionary: CURRENCY recordCount: {} aliasCount: {}",
                    currencies.size(), aliases.size() - 1);
        }
    }

    /**
     * 从固定代码 Hash 一次读取完整性标记和全部查询别名。
     *
     * <p>{@code __READY__=v1} 表示 Hash 已由同一个 HSET 命令完整写入。标记有效时，任一字段
     * 缺失都是权威未命中；多个字段反序列化后不相等也按 AND 条件不成立处理。Redis 连接异常
     * 与 Hash 尚未建立使用不同状态，使前者走精确 SQL，后者执行一次全量重建。</p>
     *
     * @param cacheKey Redis Hash 物理 Key
     * @param fields 包含完整性标记和本次查询代码的字段列表
     * @param valueType Hash 值反序列化类型
     * @param dictionary 日志中的字典类型
     * @param <T> ISO 国家或币种信息类型
     * @return Hash 命中结果、未建立状态或 Redis 不可用状态
     */
    private <T> CodeCacheReadResult<T> readCodeCache(String cacheKey,
                                                     List<Object> fields,
                                                     Class<T> valueType,
                                                     String dictionary) {
        if (stringRedisTemplate == null || !StringUtils.hasText(cacheKey)) {
            return CodeCacheReadResult.unavailable();
        }
        List<Object> cachedValues;
        try {
            cachedValues = stringRedisTemplate.opsForHash().multiGet(cacheKey, fields);
        } catch (RuntimeException exception) {
            log.warn("读取 ISO 代码 Hash 失败，dictionary: {}，异常类型: {}",
                    dictionary, exception.getClass().getSimpleName());
            return CodeCacheReadResult.unavailable();
        }
        if (cachedValues == null || cachedValues.size() != fields.size()
                || !CODE_INDEX_VERSION.equals(stringValue(cachedValues.get(0)))) {
            return CodeCacheReadResult.missing();
        }
        try {
            T resolved = null;
            for (int index = 1; index < cachedValues.size(); index++) {
                String json = stringValue(cachedValues.get(index));
                if (!StringUtils.hasText(json)) {
                    return CodeCacheReadResult.present(List.of());
                }
                T candidate = JsonUtils.parseObject(json, valueType);
                if (candidate == null) {
                    throw new IllegalStateException("ISO code cache payload is null");
                }
                if (resolved != null && !Objects.equals(resolved, candidate)) {
                    return CodeCacheReadResult.present(List.of());
                }
                resolved = candidate;
            }
            return resolved == null
                    ? CodeCacheReadResult.present(List.of())
                    : CodeCacheReadResult.present(List.of(resolved));
        } catch (RuntimeException exception) {
            // 损坏载荷不能作为权威未命中；下一次从库全量加载会替换整个固定 Hash。
            log.warn("ISO 代码 Hash 载荷无效，将从数据库重建，dictionary: {}，异常类型: {}",
                    dictionary, exception.getClass().getSimpleName());
            return CodeCacheReadResult.missing();
        }
    }

    /** 写入不带 TTL 的完整代码 Hash；先删除旧版本字段，防止已移除代码残留。 */
    private boolean writeCodeCache(String cacheKey, Map<String, String> aliases, String dictionary) {
        if (stringRedisTemplate == null || !StringUtils.hasText(cacheKey)) {
            return false;
        }
        try {
            stringRedisTemplate.delete(cacheKey);
            // aliases 同时包含全部代码别名和 ready 标记，Redis HSET 对单条命令执行保持原子性。
            stringRedisTemplate.opsForHash().putAll(cacheKey, aliases);
            return true;
        } catch (RuntimeException exception) {
            log.warn("写入 ISO 代码 Hash 失败，dictionary: {}，异常类型: {}",
                    dictionary, exception.getClass().getSimpleName());
            return false;
        }
    }

    /** 为本次国家查询构造一次 HMGET 所需字段，顺序与请求条件保持稳定。 */
    private List<Object> countryCodeFields(String alpha2, String alpha3, String numeric) {
        List<Object> fields = new ArrayList<>(4);
        fields.add(CODE_INDEX_READY_FIELD);
        addCodeField(fields, ALPHA2_FIELD_PREFIX, alpha2);
        addCodeField(fields, ALPHA3_FIELD_PREFIX, alpha3);
        addCodeField(fields, NUMERIC_FIELD_PREFIX, numeric);
        return List.copyOf(fields);
    }

    /** 为本次币种查询构造一次 HMGET 所需字段。 */
    private List<Object> currencyCodeFields(String alphabeticCode, String numericCode) {
        List<Object> fields = new ArrayList<>(3);
        fields.add(CODE_INDEX_READY_FIELD);
        addCodeField(fields, ALPHA3_FIELD_PREFIX, alphabeticCode);
        addCodeField(fields, NUMERIC_FIELD_PREFIX, numericCode);
        return List.copyOf(fields);
    }

    private void addCodeField(List<Object> fields, String prefix, String value) {
        if (value != null) {
            fields.add(prefix + value);
        }
    }

    /** 从完整国家记录建立 alpha-2、alpha-3 和 numeric 三类固定别名。 */
    private Map<String, String> countryAliases(List<IsoCountryInfo> countries) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (IsoCountryInfo country : countries) {
            String json = JsonUtils.toJsonString(country);
            putAlias(aliases, ALPHA2_FIELD_PREFIX, country.alpha2(), json);
            putAlias(aliases, ALPHA3_FIELD_PREFIX, country.alpha3(), json);
            putAlias(aliases, NUMERIC_FIELD_PREFIX, country.numeric(), json);
        }
        aliases.put(CODE_INDEX_READY_FIELD, CODE_INDEX_VERSION);
        return aliases;
    }

    /** 从完整币种记录建立 alphabetic 和 numeric 两类固定别名。 */
    private Map<String, String> currencyAliases(List<IsoCurrencyInfo> currencies) {
        Map<String, String> aliases = new LinkedHashMap<>();
        for (IsoCurrencyInfo currency : currencies) {
            String json = JsonUtils.toJsonString(currency);
            putAlias(aliases, ALPHA3_FIELD_PREFIX, currency.alphabeticCode(), json);
            putAlias(aliases, NUMERIC_FIELD_PREFIX, currency.numericCode(), json);
        }
        aliases.put(CODE_INDEX_READY_FIELD, CODE_INDEX_VERSION);
        return aliases;
    }

    private void putAlias(Map<String, String> aliases, String prefix, String code, String json) {
        if (StringUtils.hasText(code)) {
            aliases.put(prefix + code, json);
        }
    }

    /** 对从库重建结果应用与 Hash 查询一致的国家代码 AND 语义。 */
    private List<IsoCountryInfo> filterCountriesByCodes(List<IsoCountryInfo> countries,
                                                        String alpha2,
                                                        String alpha3,
                                                        String numeric) {
        return countries.stream()
                .filter(country -> alpha2 == null || alpha2.equals(country.alpha2()))
                .filter(country -> alpha3 == null || alpha3.equals(country.alpha3()))
                .filter(country -> numeric == null || numeric.equals(country.numeric()))
                .toList();
    }

    /** 对从库重建结果应用与 Hash 查询一致的币种代码 AND 语义。 */
    private List<IsoCurrencyInfo> filterCurrenciesByCodes(List<IsoCurrencyInfo> currencies,
                                                          String alphabeticCode,
                                                          String numericCode) {
        return currencies.stream()
                .filter(currency -> alphabeticCode == null
                        || alphabeticCode.equals(currency.alphabeticCode()))
                .filter(currency -> numericCode == null || numericCode.equals(currency.numericCode()))
                .toList();
    }

    /** Redis 不可用时按全部已提供国家代码执行一次精确从库查询。 */
    private List<IsoCountryInfo> loadCountriesByCodesFromDatabase(String alpha2,
                                                                 String alpha3,
                                                                 String numeric) {
        LambdaQueryWrapper<IsoCountryDO> queryWrapper = new LambdaQueryWrapper<IsoCountryDO>()
                .eq(IsoCountryDO::getStatus, STATUS_ENABLED)
                .eq(IsoCountryDO::getDeleted, NOT_DELETED)
                .eq(alpha2 != null, IsoCountryDO::getAlpha2Code, alpha2)
                .eq(alpha3 != null, IsoCountryDO::getAlpha3Code, alpha3)
                .eq(numeric != null, IsoCountryDO::getNumericCode, numeric);
        return loadDatabaseValues(() -> countryMapper.selectList(queryWrapper)
                .stream()
                .map(this::toCountryInfo)
                .toList());
    }

    /** Redis 不可用时按全部已提供币种代码执行一次精确从库查询。 */
    private List<IsoCurrencyInfo> loadCurrenciesByCodesFromDatabase(String alphabeticCode,
                                                                   String numericCode) {
        LambdaQueryWrapper<IsoCurrencyDO> queryWrapper = new LambdaQueryWrapper<IsoCurrencyDO>()
                .eq(IsoCurrencyDO::getStatus, STATUS_ENABLED)
                .eq(IsoCurrencyDO::getDeleted, NOT_DELETED)
                .eq(alphabeticCode != null, IsoCurrencyDO::getAlpha3Code, alphabeticCode)
                .eq(numericCode != null, IsoCurrencyDO::getNumericCode, numericCode);
        return loadDatabaseValues(() -> currencyMapper.selectList(queryWrapper)
                .stream()
                .map(this::toCurrencyInfo)
                .sorted(Comparator.comparing(IsoCurrencyInfo::alphabeticCode))
                .toList());
    }

    /** 统一处理 ISO 从库读取异常，禁止数据库故障时返回内置伪数据。 */
    private <T> List<T> loadDatabaseValues(Supplier<List<T>> databaseLoader) {
        try {
            List<T> values = databaseLoader.get();
            if (values == null) {
                throw new IllegalStateException("ISO dictionary database loader returned null");
            }
            return values;
        } catch (DataAccessException exception) {
            log.warn("ISO 字典数据库读取失败，未使用非权威内置数据兜底，异常类型: {}",
                    exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY, exception);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 从常驻缓存读取列表，缓存缺失时从数据库加载权威数据。
     *
     * <p>缓存读取明确区分 Key 缺失、Redis 不可用和已缓存空数组。数据库成功返回的列表
     * 无论是否为空都属于权威结果；仅在失效门禁允许且 Redis 可用时尝试写入永久快照。
     * 数据库不可用时不得使用非权威内置字典冒充业务数据。</p>
     *
     * @param cacheName        受管缓存名称
     * @param cacheKey         常驻缓存 Key；未配置统一解析器时允许为空
     * @param cacheBusinessKey 失效门禁使用的稳定业务键
     * @param typeReference    缓存 JSON 反序列化类型
     * @param databaseLoader   DB 加载器
     * @param <T>              字典数据类型
     * @return 字典列表
     * @throws ServiceException 数据库读取失败时抛出
     */
    private <T> List<T> loadFromCache(String cacheName,
                                      String cacheKey,
                                      String cacheBusinessKey,
                                      TypeReference<List<T>> typeReference,
                                      Supplier<List<T>> databaseLoader) {
        return loadFromCache(
                cacheName,
                cacheKey,
                cacheBusinessKey,
                typeReference,
                databaseLoader,
                ignored -> {
                }
        );
    }

    /**
     * 从常驻缓存读取列表，并在数据库回源成功后用同一份快照构建关联缓存。
     *
     * <p>读取顺序固定为缓存优先、数据库兜底。缓存命中时立即返回，包括已缓存的空数组；
     * Key 缺失、缓存载荷损坏或 Redis 不可用时回源数据库。失效门禁处于 pending 或状态未知时，
     * 本次数据库结果仍返回调用方，但跳过全量快照回写，避免将从库复制期间的旧值固化为永久缓存。
     * 需要构建关联缓存时，写入器接收本次完整数据库结果，并负责校验对应缓存的失效门禁。</p>
     *
     * @param cacheName              受管缓存名称
     * @param cacheKey               当前列表缓存物理 Key
     * @param cacheBusinessKey       当前列表缓存业务键
     * @param typeReference          缓存 JSON 反序列化类型
     * @param databaseLoader         DB 加载器
     * @param databaseCacheWriter    使用同一份数据库结果构建关联缓存的写入器
     * @param <T>                    字典数据类型
     * @return 字典列表
     * @throws ServiceException 数据库读取失败时抛出
     */
    private <T> List<T> loadFromCache(String cacheName,
                                      String cacheKey,
                                      String cacheBusinessKey,
                                      TypeReference<List<T>> typeReference,
                                      Supplier<List<T>> databaseLoader,
                                      Consumer<List<T>> databaseCacheWriter) {
        // 失效门禁 pending 期间保留旧快照可读，避免复制延迟把查询流量压到数据库。
        CacheReadResult<T> cacheRead = readCache(cacheKey, typeReference);
        if (cacheRead.status() == CacheReadStatus.PRESENT) {
            return cacheRead.values();
        }
        boolean cacheWriteAllowed = isCacheWriteAllowed(cacheName, cacheBusinessKey);
        try {
            List<T> databaseValues = databaseLoader.get();
            if (databaseValues == null) {
                throw new IllegalStateException("ISO dictionary database loader returned null");
            }
            // 回源前检查到失效门禁 pending 或状态未知时，跳过本次永久快照回写。
            if (cacheWriteAllowed) {
                writeCache(cacheKey, databaseValues);
            }
            // 国家和币种的代码 Hash 必须与本次全量快照来自同一次从库读取。
            databaseCacheWriter.accept(databaseValues);
            return databaseValues;
        } catch (DataAccessException exception) {
            log.warn(
                    "ISO 字典数据库读取失败，未使用非权威内置数据兜底，异常类型: {}",
                    exception.getClass().getSimpleName()
            );
            throw new ServiceException(ApiResultEnum.NETWORK_BUSY, exception);
        }
    }

    /**
     * 从 Redis 读取字典缓存。
     *
     * @param cacheKey      Redis 缓存 Key
     * @param typeReference 反序列化目标类型
     * @param <T>           字典数据类型
     * @return 带 PRESENT、MISSING 或 UNAVAILABLE 状态的缓存读取结果
     */
    private <T> CacheReadResult<T> readCache(String cacheKey, TypeReference<List<T>> typeReference) {
        if (stringRedisTemplate == null || !StringUtils.hasText(cacheKey)) {
            return CacheReadResult.unavailable();
        }
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson == null) {
                return CacheReadResult.missing();
            }
            List<T> values = JsonUtils.parseObject(cachedJson, typeReference);
            if (values == null) {
                throw new IllegalStateException("ISO dictionary cache payload is null");
            }
            return CacheReadResult.present(values);
        } catch (RuntimeException exception) {
            log.warn("读取 ISO 字典 Redis 缓存失败，异常类型: {}",
                    exception.getClass().getSimpleName());
            return CacheReadResult.unavailable();
        }
    }

    /**
     * 写入 Redis 常驻字典缓存。
     *
     * <p>写入不携带 TTL。数据生命周期由管理端业务变更触发的精确失效控制，
     * 数据库始终是事实来源。</p>
     *
     * @param cacheKey Redis 缓存 Key
     * @param values   数据库成功返回的字典列表，允许为空
     * @param <T>      字典数据类型
     */
    private <T> void writeCache(String cacheKey, List<T> values) {
        if (stringRedisTemplate == null || !StringUtils.hasText(cacheKey)) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonString(values));
        } catch (RuntimeException exception) {
            log.warn("写入 ISO 字典 Redis 缓存失败，异常类型: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 构造新命名规则的 ISO 字典 Key。
     *
     * <p>未提供业务片段时默认使用 {@code all}，生成全量快照 Key；提供 {@code codes} 等片段时，
     * 按传入顺序生成对应的派生缓存 Key。所有 Key 均包含系统和环境前缀，避免跨环境冲突。</p>
     *
     * @param business         国家或币种业务用途
     * @param businessSegments 可选业务片段；为空时默认使用 {@code all}
     * @return {@code acquiring:{environment}:iso:{business}:{segments}}；解析器未配置时返回 {@code null}
     */
    private String newCacheKey(String business, String... businessSegments) {
        return keyResolver == null
                ? null
                : keyResolver.businessKey(
                        ISO_CACHE_DOMAIN,
                        business,
                        businessSegments.length == 0
                                ? new String[]{SNAPSHOT_BUSINESS_KEY}
                                : businessSegments
                );
    }

    /**
     * 判断受管 ISO 缓存是否允许回写。
     *
     * <p>门禁明确处于空闲状态时才允许写入。未启用失效门禁组件时保持独立组件的降级能力；
     * 门禁状态读取失败时按禁止写入处理，避免在状态未知时固化可能过期的数据。</p>
     *
     * @param cacheName   受管缓存名称
     * @param businessKey 失效门禁使用的稳定业务键
     * @return 门禁明确空闲时返回 {@code true}
     */
    private boolean isCacheWriteAllowed(String cacheName, String businessKey) {
        if (invalidationGuard == null) {
            return true;
        }
        try {
            return !invalidationGuard.isPending(cacheName, businessKey);
        } catch (RuntimeException exception) {
            log.warn(
                    "ISO 字典缓存门禁状态读取失败，cacheName: {}，异常类型: {}",
                    cacheName,
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }

    /**
     * 判断派生代码索引是否允许回写。
     *
     * <p>代码索引由全量快照派生，因此必须同时确认 {@code all} 与 {@code codes} 两个失效门禁
     * 均为空闲。若其中一个管理端失效事件仍在等待从库可见或 Redis 删除重试，禁止把旧全量
     * 快照重新固化为永久代码索引。</p>
     *
     * @param cacheName 国家或币种受管缓存名称
     * @return 两个关联缓存均允许写入时返回 {@code true}
     */
    private boolean isCodeIndexWriteAllowed(String cacheName) {
        return isCacheWriteAllowed(cacheName, SNAPSHOT_BUSINESS_KEY)
                && isCacheWriteAllowed(cacheName, CODE_INDEX_BUSINESS_KEY);
    }

    /**
     * 在管理端事务中登记可靠失效；独立组件环境没有协调器时执行直接删除。
     *
     * <p>存在事务失效协调器时，仅登记包含缓存名称和业务键的可靠失效意图，由协调器在从库确认
     * 业务事务可见后执行精确删除；未装配协调器时直接删除给定物理 Key。</p>
     *
     * @param cacheName   受管缓存名称
     * @param businessKey 失效事件使用的稳定业务键
     * @param cacheKey    Redis 物理键
     */
    private void invalidate(String cacheName, String businessKey, String cacheKey) {
        if (invalidationCoordinator != null) {
            invalidationCoordinator.prepare(cacheName, businessKey);
            return;
        }
        evictCache(cacheKey);
    }

    /**
     * 删除指定 ISO 常驻缓存。该删除只由真实业务变更触发。
     *
     * @param cacheKey 新命名规则缓存 Key；允许为空
     * @throws RuntimeException Redis 删除失败时抛出，由管理端变更链路决定回滚或重试
     */
    private void evictCache(String cacheKey) {
        if (stringRedisTemplate == null || !StringUtils.hasText(cacheKey)) {
            return;
        }
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (RuntimeException exception) {
            log.warn(
                    "ISO 字典 Redis 缓存失效失败，异常类型: {}",
                    exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    /**
     * 从 base_iso_country 读取启用国家地区。
     *
     * @return 启用国家地区列表
     */
    private List<IsoCountryInfo> loadCountriesFromDatabase() {
        LambdaQueryWrapper<IsoCountryDO> queryWrapper = new LambdaQueryWrapper<IsoCountryDO>()
                .eq(IsoCountryDO::getStatus, STATUS_ENABLED)
                .eq(IsoCountryDO::getDeleted, NOT_DELETED)
                .orderByAsc(IsoCountryDO::getAlpha2Code);
        return countryMapper.selectList(queryWrapper)
                .stream()
                .map(this::toCountryInfo)
                .toList();
    }

    /**
     * 从 base_iso_currency 读取启用币种。
     *
     * @return 启用币种列表
     */
    private List<IsoCurrencyInfo> loadCurrenciesFromDatabase() {
        LambdaQueryWrapper<IsoCurrencyDO> queryWrapper = new LambdaQueryWrapper<IsoCurrencyDO>()
                .eq(IsoCurrencyDO::getStatus, STATUS_ENABLED)
                .eq(IsoCurrencyDO::getDeleted, NOT_DELETED)
                .orderByAsc(IsoCurrencyDO::getAlpha3Code);
        return currencyMapper.selectList(queryWrapper)
                .stream()
                .map(this::toCurrencyInfo)
                .sorted(Comparator.comparing(IsoCurrencyInfo::alphabeticCode))
                .toList();
    }

    /**
     * 从币种表读取纯展示信息。
     */
    private List<IsoCurrencyPresentationInfo> loadCurrencyPresentationsFromDatabase() {
        LambdaQueryWrapper<IsoCurrencyDO> queryWrapper = new LambdaQueryWrapper<IsoCurrencyDO>()
                .eq(IsoCurrencyDO::getStatus, STATUS_ENABLED)
                .eq(IsoCurrencyDO::getDeleted, NOT_DELETED)
                .orderByAsc(IsoCurrencyDO::getAlpha3Code);
        return currencyMapper.selectList(queryWrapper)
                .stream()
                .map(this::toCurrencyPresentationInfo)
                .sorted(Comparator.comparing(IsoCurrencyPresentationInfo::alphabeticCode))
                .toList();
    }

    /**
     * 转换国家地区数据库实体为核心 ISO 信息对象。
     *
     * @param countryDO 国家地区数据库实体
     * @return 核心 ISO 国家地区信息
     */
    private IsoCountryInfo toCountryInfo(IsoCountryDO countryDO) {
        return new IsoCountryInfo(
                countryDO.getAlpha2Code(),
                countryDO.getAlpha3Code(),
                countryDO.getNumericCode(),
                countryDO.getEnglishName(),
                countryDO.getShortEnglishName(),
                countryDO.getChineseName(),
                countryDO.getContinentCode(),
                countryDO.getContinentName(),
                countryDO.getFlagEmoji(),
                countryDO.getPrimaryLanguageCode(),
                countryDO.getPrimaryLanguageEnglish(),
                countryDO.getPrimaryLanguageChinese(),
                countryDO.getCurrencyAlpha3Code()
        );
    }

    /**
     * 转换币种数据库实体为核心 ISO 信息对象。
     *
     * @param currencyDO 币种数据库实体
     * @return 核心 ISO 币种信息
     */
    private IsoCurrencyInfo toCurrencyInfo(IsoCurrencyDO currencyDO) {
        return new IsoCurrencyInfo(
                currencyDO.getAlpha3Code(),
                currencyDO.getNumericCode(),
                currencyDO.getEnglishName(),
                currencyDO.getChineseName(),
                currencyDO.getFractionDigits(),
                currencyDO.getMinorUnitMultiplier(),
                currencyDO.getMinimumAmount(),
                currencyDO.getCurrencySymbol()
        );
    }

    /** 转换币种数据库实体为界面展示信息。 */
    private IsoCurrencyPresentationInfo toCurrencyPresentationInfo(IsoCurrencyDO currencyDO) {
        return new IsoCurrencyPresentationInfo(
                currencyDO.getAlpha3Code(),
                currencyDO.getChineseName(),
                currencyDO.getEnglishName(),
                currencyDO.getCurrencySymbol(),
                safeIconKey(currencyDO)
        );
    }

    private String safeIconKey(IsoCurrencyDO currencyDO) {
        try {
            return IsoCurrencyIconKey.normalize(currencyDO.getAlpha3Code(), currencyDO.getIconKey());
        } catch (IllegalArgumentException exception) {
            log.warn("忽略非法币种展示图标键，currency: {}", currencyDO.getAlpha3Code());
            return null;
        }
    }

    /**
     * 判断国家地区是否被关键字模糊命中。
     *
     * @param country           国家地区信息
     * @param normalizedKeyword 标准化关键字
     * @return true 表示命中
     */
    private boolean countryMatches(IsoCountryInfo country, String normalizedKeyword) {
        return contains(country.alpha2(), normalizedKeyword)
                || contains(country.alpha3(), normalizedKeyword)
                || contains(country.numeric(), normalizedKeyword)
                || contains(country.englishName(), normalizedKeyword)
                || contains(country.shortEnglishName(), normalizedKeyword)
                || contains(country.chineseName(), normalizedKeyword)
                || contains(country.continentCode(), normalizedKeyword)
                || contains(country.continentName(), normalizedKeyword)
                || contains(country.primaryLanguageCode(), normalizedKeyword)
                || contains(country.primaryLanguageEnglish(), normalizedKeyword)
                || contains(country.primaryLanguageChinese(), normalizedKeyword)
                || contains(country.currencyAlpha3Code(), normalizedKeyword);
    }

    /**
     * 判断国家地区是否被标准代码或名称精确命中。
     *
     * @param country         国家地区信息
     * @param normalizedValue 标准化查询值
     * @return true 表示精确命中
     */
    private boolean exactCountryMatches(IsoCountryInfo country, String normalizedValue) {
        return equalsNormalized(country.alpha2(), normalizedValue)
                || equalsNormalized(country.alpha3(), normalizedValue)
                || equalsNormalized(country.numeric(), normalizedValue)
                || equalsNormalized(country.englishName(), normalizedValue)
                || equalsNormalized(country.shortEnglishName(), normalizedValue)
                || equalsNormalized(country.chineseName(), normalizedValue);
    }

    /**
     * 判断币种是否被关键字模糊命中。
     *
     * @param currency          币种信息
     * @param normalizedKeyword 标准化关键字
     * @return true 表示命中
     */
    private boolean currencyMatches(IsoCurrencyInfo currency, String normalizedKeyword) {
        return contains(currency.alphabeticCode(), normalizedKeyword)
                || contains(currency.numericCode(), normalizedKeyword)
                || contains(currency.englishName(), normalizedKeyword)
                || contains(currency.chineseName(), normalizedKeyword)
                || contains(currency.currencySymbol(), normalizedKeyword);
    }

    /**
     * 判断币种是否被标准代码或名称精确命中。
     *
     * @param currency        币种信息
     * @param normalizedValue 标准化查询值
     * @return true 表示精确命中
     */
    private boolean exactCurrencyMatches(IsoCurrencyInfo currency, String normalizedValue) {
        return equalsNormalized(currency.alphabeticCode(), normalizedValue)
                || equalsNormalized(currency.numericCode(), normalizedValue)
                || equalsNormalized(currency.englishName(), normalizedValue)
                || equalsNormalized(currency.chineseName(), normalizedValue);
    }

    /**
     * 判断标准化后文本是否包含关键字。
     *
     * @param value             原始文本
     * @param normalizedKeyword 标准化关键字
     * @return true 表示包含
     */
    private boolean contains(String value, String normalizedKeyword) {
        return normalize(value).contains(normalizedKeyword);
    }

    /**
     * 判断文本标准化后是否相等。
     *
     * @param value           原始文本
     * @param normalizedValue 标准化查询值
     * @return true 表示相等
     */
    private boolean equalsNormalized(String value, String normalizedValue) {
        return normalize(value).equals(normalizedValue);
    }

    /**
     * 标准化查询文本。
     *
     * @param value 原始文本
     * @return 去除空白、短横线、下划线并转大写后的文本
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
                .toUpperCase(Locale.ROOT);
    }

    /** 固定代码 Hash 的读取状态。 */
    private enum CodeCacheReadStatus {
        PRESENT,
        MISSING,
        UNAVAILABLE
    }

    /**
     * 固定代码 Hash 读取结果。PRESENT 可携带空列表，表示完整索引中的权威未命中。
     */
    private record CodeCacheReadResult<T>(CodeCacheReadStatus status, List<T> values) {

        private static <T> CodeCacheReadResult<T> present(List<T> values) {
            return new CodeCacheReadResult<>(CodeCacheReadStatus.PRESENT, List.copyOf(values));
        }

        private static <T> CodeCacheReadResult<T> missing() {
            return new CodeCacheReadResult<>(CodeCacheReadStatus.MISSING, List.of());
        }

        private static <T> CodeCacheReadResult<T> unavailable() {
            return new CodeCacheReadResult<>(CodeCacheReadStatus.UNAVAILABLE, List.of());
        }
    }

    /** Redis 快照读取状态，PRESENT 状态允许携带合法空列表。 */
    private enum CacheReadStatus {
        PRESENT,
        MISSING,
        UNAVAILABLE
    }

    /** 缓存读取值与状态，避免用空列表同时表示“缓存不存在”和“已缓存空结果”。 */
    private record CacheReadResult<T>(CacheReadStatus status, List<T> values) {

        private static <T> CacheReadResult<T> present(List<T> values) {
            return new CacheReadResult<>(CacheReadStatus.PRESENT, List.copyOf(values));
        }

        private static <T> CacheReadResult<T> missing() {
            return new CacheReadResult<>(CacheReadStatus.MISSING, List.of());
        }

        private static <T> CacheReadResult<T> unavailable() {
            return new CacheReadResult<>(CacheReadStatus.UNAVAILABLE, List.of());
        }
    }
}
