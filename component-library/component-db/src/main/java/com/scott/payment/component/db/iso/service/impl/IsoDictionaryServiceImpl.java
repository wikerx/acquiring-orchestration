package com.scott.payment.component.db.iso.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCountryResolver;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
@DS(DataSourceName.SLAVE)
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

    /**
     * 创建 ISO 字典服务实现。
     *
     * @param countryMapper               国家地区 Mapper
     * @param currencyMapper              币种 Mapper
     * @param stringRedisTemplateProvider Redis 模板提供器
     * @param keyResolverProvider         统一 Redis Key 解析器提供器
     */
    public IsoDictionaryServiceImpl(IsoCountryMapper countryMapper,
                                    IsoCurrencyMapper currencyMapper,
                                    ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                    ObjectProvider<PaymentRedisKeyResolver> keyResolverProvider) {
        this.countryMapper = countryMapper;
        this.currencyMapper = currencyMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.keyResolver = keyResolverProvider.getIfAvailable();
    }

    /**
     * 查询全部启用国家地区。
     *
     * @return 启用国家地区列表
     */
    @Override
    public List<IsoCountryInfo> listCountries() {
        return loadFromCache(
                newCacheKey(COUNTRY_CACHE_BUSINESS),
                new TypeReference<List<IsoCountryInfo>>() {
                },
                this::loadCountriesFromDatabase,
                IsoCountryResolver::listIndexedCountries
        );
    }

    /**
     * 按关键字查询国家地区。
     *
     * @param keyword 查询关键字，空值时返回全部启用国家地区
     * @return 命中的国家地区列表
     */
    @Override
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
    public List<IsoCurrencyInfo> listCurrencies() {
        return loadFromCache(
                newCacheKey(CURRENCY_CACHE_BUSINESS),
                new TypeReference<List<IsoCurrencyInfo>>() {
                },
                this::loadCurrenciesFromDatabase,
                IsoCurrencyResolver::listIndexedCurrencies
        );
    }

    /**
     * 按关键字查询币种。
     *
     * @param keyword 查询关键字，空值时返回全部启用币种
     * @return 命中的币种列表
     */
    @Override
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
    public long toMinorUnit(BigDecimal amount, String currencyValue) {
        IsoCurrencyInfo currency = getCurrency(currencyValue)
                .orElseThrow(() -> new IllegalArgumentException("currency can not be resolved"));
        return IsoCurrencyResolver.toMinorUnit(amount, currency);
    }

    /**
     * 删除国家地区常驻缓存，使下一次读取从数据库重建权威快照。
     *
     * <p>管理端国家或地区关联数据提交成功后调用。Redis 删除失败会向上抛出，
     * 防止业务变更后继续静默使用永久旧值。</p>
     */
    @Override
    public void evictCountries() {
        evictCache(newCacheKey(COUNTRY_CACHE_BUSINESS));
    }

    /**
     * 删除币种常驻缓存，使下一次读取从数据库重建权威快照。
     *
     * <p>管理端币种或地区币种关联数据提交成功后调用。Redis 删除失败会向上抛出，
     * 防止业务变更后继续静默使用永久旧值。</p>
     */
    @Override
    public void evictCurrencies() {
        evictCache(newCacheKey(CURRENCY_CACHE_BUSINESS));
    }

    /**
     * 从常驻缓存读取列表，缓存缺失时从数据库加载权威数据。
     *
     * <p>只有数据库成功返回非空结果时才写入 Redis。数据库为空或不可用时返回内置
     * ISO 数据服务当前请求，但不写缓存，避免临时兜底长期覆盖管理端维护的数据。</p>
     *
     * @param cacheKey       常驻缓存 Key；未配置统一解析器时允许为空
     * @param typeReference  缓存 JSON 反序列化类型
     * @param databaseLoader DB 加载器
     * @param fallbackLoader 内置数据兜底加载器
     * @param <T>            字典数据类型
     * @return 字典列表
     */
    private <T> List<T> loadFromCache(String cacheKey,
                                      TypeReference<List<T>> typeReference,
                                      Supplier<List<T>> databaseLoader,
                                      Supplier<List<T>> fallbackLoader) {
        List<T> cachedValues = readCache(cacheKey, typeReference);
        if (!cachedValues.isEmpty()) {
            return cachedValues;
        }
        try {
            List<T> databaseValues = databaseLoader.get();
            if (!databaseValues.isEmpty()) {
                writeCache(cacheKey, databaseValues);
                return databaseValues;
            }
            log.warn("ISO 字典数据库结果为空，临时使用内置 ISO 数据兜底且不写入常驻缓存");
        } catch (DataAccessException exception) {
            log.warn(
                    "ISO 字典数据库读取失败，临时使用内置 ISO 数据兜底且不写入常驻缓存，异常类型: {}，原因: {}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
        }
        return fallbackLoader.get();
    }

    /**
     * 从 Redis 读取字典缓存。
     *
     * @param cacheKey      Redis 缓存 Key
     * @param typeReference 反序列化目标类型
     * @param <T>           字典数据类型
     * @return 缓存字典列表；缓存不存在或 Redis 异常时返回空列表
     */
    private <T> List<T> readCache(String cacheKey, TypeReference<List<T>> typeReference) {
        if (stringRedisTemplate == null || !StringUtils.hasText(cacheKey)) {
            return List.of();
        }
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            List<T> values = JsonUtils.parseObject(cachedJson, typeReference);
            return values == null ? List.of() : values;
        } catch (RuntimeException exception) {
            log.warn("读取 ISO 字典 Redis 缓存失败，cacheKey: {}，原因: {}", cacheKey, exception.getMessage());
            return List.of();
        }
    }

    /**
     * 写入 Redis 常驻字典缓存。
     *
     * <p>写入不携带 TTL。数据生命周期由管理端业务变更触发的精确失效控制，
     * 数据库始终是事实来源。</p>
     *
     * @param cacheKey Redis 缓存 Key
     * @param values   数据库成功返回的非空字典列表
     * @param <T>      字典数据类型
     */
    private <T> void writeCache(String cacheKey, List<T> values) {
        if (stringRedisTemplate == null || !StringUtils.hasText(cacheKey) || values.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonString(values));
        } catch (RuntimeException exception) {
            log.warn("写入 ISO 字典 Redis 缓存失败，cacheKey: {}，原因: {}", cacheKey, exception.getMessage());
        }
    }

    /**
     * 构造新命名规则的 ISO 字典 Key。
     *
     * @param business 国家或币种业务用途
     * @return acquiring:{environment}:iso:{business}；解析器未配置时返回 null
     */
    private String newCacheKey(String business) {
        return keyResolver == null ? null : keyResolver.businessKey(ISO_CACHE_DOMAIN, business);
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
                    "ISO 字典 Redis 缓存失效失败，cacheKey: {}，异常类型: {}，原因: {}",
                    cacheKey,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
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
}
