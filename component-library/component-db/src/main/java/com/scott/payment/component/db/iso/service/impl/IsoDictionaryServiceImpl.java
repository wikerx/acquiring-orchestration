package com.scott.payment.component.db.iso.service.impl;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCountryResolver;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
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
 * @description : ISO 国家地区与币种基础字典公共查询服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoDictionaryServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Iso Dictionary Service Impl，位于 component-library/component-db 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Slf4j
@Service
@DS(DataSourceName.SLAVE)
public class IsoDictionaryServiceImpl implements IsoDictionaryService {

    /**
     * 启用状态值。
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 未删除状态值。
     */
    private static final int NOT_DELETED = 0;

    /**
     * ISO 国家地区全量缓存 Key。
     */
    private static final String COUNTRY_CACHE_KEY = "payment:iso:country:all";

    /**
     * ISO 币种全量缓存 Key。
     */
    private static final String CURRENCY_CACHE_KEY = "payment:iso:currency:all";

    /**
     * ISO 字典缓存过期时间。基础字典低频变更，保留 12 小时缓存即可兼顾性能和更新传播。
     */
    private static final Duration ISO_CACHE_TTL = Duration.ofHours(12);

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
     * 创建 ISO 字典服务实现。
     *
     * @param countryMapper               国家地区 Mapper
     * @param currencyMapper              币种 Mapper
     * @param stringRedisTemplateProvider Redis 模板提供器
     */
    public IsoDictionaryServiceImpl(IsoCountryMapper countryMapper,
                                    IsoCurrencyMapper currencyMapper,
                                    ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.countryMapper = countryMapper;
        this.currencyMapper = currencyMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    /**
     * 查询全部启用国家地区。
     *
     * @return 启用国家地区列表
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<IsoCountryInfo> listCountries() {
        return loadFromCache(
                COUNTRY_CACHE_KEY,
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param keyword 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param continentCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param currencyAlpha3Code 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<IsoCurrencyInfo> listCurrencies() {
        return loadFromCache(
                CURRENCY_CACHE_KEY,
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
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param keyword 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @param amount 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param currencyValue 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 转换收单支付数据结构，避免数据库实体直接暴露到外部接口。
     * @param amount 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param currencyValue 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public long toMinorUnit(BigDecimal amount, String currencyValue) {
        IsoCurrencyInfo currency = getCurrency(currencyValue)
                .orElseThrow(() -> new IllegalArgumentException("currency can not be resolved"));
        return IsoCurrencyResolver.toMinorUnit(amount, currency);
    }

    /**
     * 从缓存读取列表，缓存缺失时从 DB 加载，DB 异常时使用内置 ISO 数据兜底。
     *
     * @param cacheKey       Redis 缓存 Key
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
        List<T> values = loadFromDatabaseSafely(databaseLoader, fallbackLoader);
        writeCache(cacheKey, values);
        return values;
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
        if (stringRedisTemplate == null) {
            return List.of();
        }
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            List<T> values = JsonUtils.parseObject(cachedJson, typeReference);
            return values == null ? List.of() : values;
        } catch (RuntimeException exception) {
            log.warn("读取 ISO 字典 Redis 缓存失败，cacheKey={}，原因={}", cacheKey, exception.getMessage());
            return List.of();
        }
    }

    /**
     * 写入 Redis 字典缓存。
     *
     * @param cacheKey Redis 缓存 Key
     * @param values   字典数据列表
     * @param <T>      字典数据类型
     */
    private <T> void writeCache(String cacheKey, List<T> values) {
        if (stringRedisTemplate == null || values.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonString(values), ISO_CACHE_TTL);
        } catch (RuntimeException exception) {
            log.warn("写入 ISO 字典 Redis 缓存失败，cacheKey={}，原因={}", cacheKey, exception.getMessage());
        }
    }

    /**
     * 安全加载 DB 字典数据，DB 不可用时回退到内置 ISO 数据。
     *
     * @param databaseLoader DB 加载器
     * @param fallbackLoader 内置数据兜底加载器
     * @param <T>            字典数据类型
     * @return 字典数据列表
     */
    private <T> List<T> loadFromDatabaseSafely(Supplier<List<T>> databaseLoader, Supplier<List<T>> fallbackLoader) {
        try {
            List<T> databaseValues = databaseLoader.get();
            if (!databaseValues.isEmpty()) {
                return databaseValues;
            }
            log.warn("ISO 字典数据库结果为空，临时使用内置 ISO 数据兜底");
        } catch (DataAccessException exception) {
            log.warn("ISO 字典数据库读取失败，临时使用内置 ISO 数据兜底，原因={}", exception.getMessage());
        }
        return fallbackLoader.get();
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
