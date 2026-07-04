package com.scott.payment.component.core.iso;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Map.entry;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryResolver
 * @date : 2026-06-02 15:44
 * @email : scott_x@163.com
 * @description : ISO 3166 国家地区识别工具，支持 alpha-2、alpha-3、numeric、英文名、中文名、别名识别
 * @status : update
 */
public final class IsoCountryResolver {

    /**
     * 常用中文别名到 alpha-2 的映射，用于兼容商户资料、浏览器语言和人工录入。
     */
    private static final Map<String, String> CHINESE_ALIAS_TO_ALPHA2 = Map.ofEntries(
            entry("中国", "CN"), entry("中国大陆", "CN"), entry("大陆", "CN"), entry("美国", "US"),
            entry("英国", "GB"), entry("日本", "JP"), entry("韩国", "KR"), entry("新加坡", "SG"),
            entry("加拿大", "CA"), entry("澳大利亚", "AU"), entry("德国", "DE"), entry("法国", "FR"),
            entry("意大利", "IT"), entry("西班牙", "ES"), entry("荷兰", "NL"), entry("瑞士", "CH"),
            entry("香港", "HK"), entry("香港特别行政区", "HK"), entry("澳门", "MO"), entry("澳门特别行政区", "MO"),
            entry("台湾", "TW"), entry("巴西", "BR"), entry("墨西哥", "MX"), entry("印度", "IN"),
            entry("马来西亚", "MY"), entry("泰国", "TH"), entry("越南", "VN")
    );

    /**
     * 标准化后的国家索引，覆盖 alpha-2、alpha-3、numeric、英文全称、英文简称、中文名称和常用别名。
     */
    private static final Map<String, IsoCountryInfo> COUNTRY_INDEX = buildCountryIndex();

    /**
     * 工具类不允许实例化。
     */
    private IsoCountryResolver() {
    }

    /**
     * 根据国家代码或名称识别国家地区。
     *
     * @param value 国家代码、数字码、英文名、中文名或常用别名
     * @return 国家地区信息
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static Optional<IsoCountryInfo> resolve(String value) {
        if (!hasText(value)) {
            return Optional.empty();
        }
        return Optional.ofNullable(COUNTRY_INDEX.get(normalize(value)));
    }

    /**
     * 根据浏览器 Accept-Language 头识别最优 Locale。
     *
     * @param acceptLanguage   浏览器 Accept-Language 头，例如 zh-CN,en-US;q=0.8
     * @param supportedLocales 系统支持的 Locale 列表
     * @return 命中的 Locale；没有命中时返回空
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param acceptLanguage 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param supportedLocales 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static Optional<Locale> matchBrowserLocale(String acceptLanguage, Collection<Locale> supportedLocales) {
        if (!hasText(acceptLanguage) || supportedLocales == null || supportedLocales.isEmpty()) {
            return Optional.empty();
        }
        List<Locale.LanguageRange> languageRanges = Locale.LanguageRange.parse(acceptLanguage);
        return Optional.ofNullable(Locale.lookup(languageRanges, supportedLocales.stream().toList()));
    }

    /**
     * 根据浏览器 Accept-Language 中的地区识别国家。
     *
     * @param acceptLanguage 浏览器 Accept-Language 头
     * @return 国家地区信息；仅语言无地区时返回空
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param acceptLanguage 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static Optional<IsoCountryInfo> resolveCountryFromBrowserLanguage(String acceptLanguage) {
        if (!hasText(acceptLanguage)) {
            return Optional.empty();
        }
        return Locale.LanguageRange.parse(acceptLanguage)
                .stream()
                .map(Locale.LanguageRange::getRange)
                .map(Locale::forLanguageTag)
                .map(Locale::getCountry)
                .filter(IsoCountryResolver::hasText)
                .findFirst()
                .flatMap(IsoCountryResolver::resolve);
    }

    /**
     * 查询当前内置国家地区列表，便于单元测试、基础数据初始化和管理后台展示。
     *
     * @return 国家地区信息列表
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static List<IsoCountryInfo> listIndexedCountries() {
        return IsoStandardData.COUNTRIES
                .stream()
                .sorted(Comparator.comparing(IsoCountryInfo::alpha2))
                .toList();
    }

    /**
     * 查询当前国家索引快照。
     *
     * @return 标准化查询值到国家地区信息的映射
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static Map<String, IsoCountryInfo> indexSnapshot() {
        return COUNTRY_INDEX;
    }

    /**
     * 构建国家索引。
     *
     * @return 标准化查询值到国家信息的映射
     */
    private static Map<String, IsoCountryInfo> buildCountryIndex() {
        Map<String, IsoCountryInfo> index = new LinkedHashMap<>();
        IsoStandardData.COUNTRIES.forEach(country -> {
            putIndex(index, country.alpha2(), country);
            putIndex(index, country.alpha3(), country);
            putIndex(index, country.numeric(), country);
            putIndex(index, country.englishName(), country);
            putIndex(index, country.shortEnglishName(), country);
            putIndex(index, country.chineseName(), country);
        });
        CHINESE_ALIAS_TO_ALPHA2.forEach((alias, alpha2) -> {
            IsoCountryInfo country = index.get(normalize(alpha2));
            if (country != null) {
                putIndex(index, alias, country);
            }
        });
        return Map.copyOf(index.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new)));
    }

    /**
     * 写入索引，空值不写入。
     *
     * @param index   索引集合
     * @param key     原始索引键
     * @param country 国家地区信息
     */
    private static void putIndex(Map<String, IsoCountryInfo> index, String key, IsoCountryInfo country) {
        if (hasText(key)) {
            index.putIfAbsent(normalize(key), country);
        }
    }

    /**
     * 标准化输入文本，降低大小写、空白、短横线和下划线导致的匹配失败。
     *
     * @param value 原始文本
     * @return 标准化后的索引 key
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace(" ", "").replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
    }

    /**
     * 判断文本是否包含非空白字符。
     *
     * @param value 待判断文本
     * @return true 表示文本有内容
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
