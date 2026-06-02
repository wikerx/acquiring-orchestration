package com.scott.payment.component.core.iso;

import java.util.Collection;
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
 * @description : ISO 3166 国家地区识别工具
 * @status : create
 */
public final class IsoCountryResolver {

    /**
     * 简体中文显示名称使用的 Locale。
     */
    private static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;

    /**
     * 英文显示名称使用的 Locale。
     */
    private static final Locale EN = Locale.ENGLISH;

    /**
     * 常用跨境收单市场 ISO 3166-1 numeric 到 alpha-2 的映射。
     * <p>
     * JDK 17 的 Locale 能提供 alpha-2、alpha-3 和本地化名称，但不直接暴露国家三数字码。这里先覆盖常用收单、
     * 清算、风控和商户资料场景，后续如果需要全量 ISO 3166 表，可把该映射迁移到配置或基础字典表。
     */
    private static final Map<String, String> NUMERIC_TO_ALPHA2 = Map.ofEntries(
            entry("004", "AF"), entry("008", "AL"), entry("012", "DZ"), entry("032", "AR"),
            entry("036", "AU"), entry("040", "AT"), entry("048", "BH"), entry("050", "BD"),
            entry("056", "BE"), entry("076", "BR"), entry("100", "BG"), entry("124", "CA"),
            entry("152", "CL"), entry("156", "CN"), entry("170", "CO"), entry("188", "CR"),
            entry("191", "HR"), entry("196", "CY"), entry("203", "CZ"), entry("208", "DK"),
            entry("233", "EE"), entry("246", "FI"), entry("250", "FR"), entry("276", "DE"),
            entry("300", "GR"), entry("344", "HK"), entry("348", "HU"), entry("352", "IS"),
            entry("356", "IN"), entry("360", "ID"), entry("372", "IE"), entry("376", "IL"),
            entry("380", "IT"), entry("392", "JP"), entry("410", "KR"), entry("414", "KW"),
            entry("428", "LV"), entry("440", "LT"), entry("442", "LU"), entry("446", "MO"),
            entry("458", "MY"), entry("484", "MX"), entry("528", "NL"), entry("554", "NZ"),
            entry("578", "NO"), entry("586", "PK"), entry("604", "PE"), entry("608", "PH"),
            entry("616", "PL"), entry("620", "PT"), entry("634", "QA"), entry("642", "RO"),
            entry("682", "SA"), entry("688", "RS"), entry("702", "SG"), entry("703", "SK"),
            entry("705", "SI"), entry("710", "ZA"), entry("724", "ES"), entry("752", "SE"),
            entry("756", "CH"), entry("764", "TH"), entry("784", "AE"), entry("792", "TR"),
            entry("826", "GB"), entry("840", "US"), entry("858", "UY"), entry("704", "VN")
    );

    /**
     * 常用中文别名到 alpha-2 的映射，用于兼容商户资料、浏览器语言和人工录入。
     */
    private static final Map<String, String> CHINESE_ALIAS_TO_ALPHA2 = Map.ofEntries(
            entry("中国", "CN"), entry("中国大陆", "CN"), entry("大陆", "CN"), entry("美国", "US"),
            entry("英国", "GB"), entry("日本", "JP"), entry("韩国", "KR"), entry("新加坡", "SG"),
            entry("加拿大", "CA"), entry("澳大利亚", "AU"), entry("德国", "DE"), entry("法国", "FR"),
            entry("意大利", "IT"), entry("西班牙", "ES"), entry("荷兰", "NL"), entry("瑞士", "CH"),
            entry("香港", "HK"), entry("澳门", "MO"), entry("巴西", "BR"), entry("墨西哥", "MX"),
            entry("印度", "IN"), entry("马来西亚", "MY"), entry("泰国", "TH"), entry("越南", "VN")
    );

    /**
     * alpha-2 到洲代码的常用映射，用于风控、费率和运营统计。
     */
    private static final Map<String, String> ALPHA2_TO_CONTINENT = Map.ofEntries(
            entry("US", "NA"), entry("CA", "NA"), entry("MX", "NA"), entry("CR", "NA"),
            entry("BR", "SA"), entry("AR", "SA"), entry("CL", "SA"), entry("CO", "SA"),
            entry("PE", "SA"), entry("UY", "SA"), entry("CN", "AS"), entry("HK", "AS"),
            entry("MO", "AS"), entry("JP", "AS"), entry("KR", "AS"), entry("SG", "AS"),
            entry("MY", "AS"), entry("TH", "AS"), entry("VN", "AS"), entry("IN", "AS"),
            entry("ID", "AS"), entry("PH", "AS"), entry("AE", "AS"), entry("SA", "AS"),
            entry("QA", "AS"), entry("KW", "AS"), entry("PK", "AS"), entry("AU", "OC"),
            entry("NZ", "OC"), entry("GB", "EU"), entry("DE", "EU"), entry("FR", "EU"),
            entry("IT", "EU"), entry("ES", "EU"), entry("NL", "EU"), entry("CH", "EU"),
            entry("SE", "EU"), entry("NO", "EU"), entry("DK", "EU"), entry("FI", "EU"),
            entry("IE", "EU"), entry("PT", "EU"), entry("PL", "EU"), entry("ZA", "AF")
    );

    /**
     * 洲代码到中文名称映射。
     */
    private static final Map<String, String> CONTINENT_NAME = Map.of(
            "AS", "亚洲",
            "EU", "欧洲",
            "NA", "北美洲",
            "SA", "南美洲",
            "AF", "非洲",
            "OC", "大洋洲",
            "AN", "南极洲"
    );

    /**
     * 标准化后的国家索引，覆盖 alpha-2、alpha-3、numeric、英文名称、中文名称和常用别名。
     */
    private static final Map<String, IsoCountryInfo> COUNTRY_INDEX = buildCountryIndex();

    private IsoCountryResolver() {
    }

    /**
     * 根据国家代码或名称识别国家地区。
     *
     * @param value 国家代码、数字码、英文名、中文名或常用别名
     * @return 国家地区信息
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
     * @param acceptLanguage    浏览器 Accept-Language 头，例如 zh-CN,en-US;q=0.8
     * @param supportedLocales  系统支持的 Locale 列表
     * @return 命中的 Locale；没有命中时返回空
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
     * 查询当前内置国家索引，便于单元测试和管理后台展示。
     *
     * @return 国家信息列表
     */
    public static List<IsoCountryInfo> listIndexedCountries() {
        return COUNTRY_INDEX.values()
                .stream()
                .collect(Collectors.toMap(IsoCountryInfo::alpha2, country -> country, (left, right) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
    }

    /**
     * 构建国家索引。
     *
     * @return 标准化查询值到国家信息的映射
     */
    private static Map<String, IsoCountryInfo> buildCountryIndex() {
        Map<String, IsoCountryInfo> index = new LinkedHashMap<>();
        NUMERIC_TO_ALPHA2.forEach((numeric, alpha2) -> {
            Locale locale = new Locale("", alpha2);
            String alpha3 = safeIso3Country(locale);
            String continentCode = ALPHA2_TO_CONTINENT.getOrDefault(alpha2, "");
            IsoCountryInfo country = new IsoCountryInfo(
                    alpha2,
                    alpha3,
                    numeric,
                    locale.getDisplayCountry(EN),
                    locale.getDisplayCountry(ZH_CN),
                    continentCode,
                    CONTINENT_NAME.getOrDefault(continentCode, "")
            );
            index.put(normalize(alpha2), country);
            index.put(normalize(alpha3), country);
            index.put(normalize(numeric), country);
            index.put(normalize(country.englishName()), country);
            index.put(normalize(country.chineseName()), country);
        });
        CHINESE_ALIAS_TO_ALPHA2.forEach((alias, alpha2) -> {
            IsoCountryInfo country = index.get(normalize(alpha2));
            if (country != null) {
                index.put(normalize(alias), country);
            }
        });
        return Map.copyOf(index);
    }

    /**
     * 安全获取 ISO alpha-3 国家代码。
     *
     * @param locale 国家 Locale
     * @return alpha-3 国家代码
     */
    private static String safeIso3Country(Locale locale) {
        try {
            return locale.getISO3Country();
        } catch (Exception exception) {
            return "";
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
