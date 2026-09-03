package com.scott.payment.checkout.application;

import com.scott.payment.checkout.dto.CheckoutCountryConfigResponse;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCountryConfigApplicationService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : 收银台国家或地区配置应用服务，位于 收银台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class CheckoutCountryConfigApplicationService {

    /**
     * 收银台当前轻量文案包支持的语言。
     */
    private static final List<String> SUPPORTED_LANGUAGES = List.of("en-US", "zh-CN");

    /**
     * 默认语言。
     */
    private static final String DEFAULT_LANGUAGE = "en-US";

    /**
     * 常用国家地区优先展示顺序。
     */
    private static final Map<String, Integer> COUNTRY_SORT_OVERRIDES = Map.of(
            "HK", 10,
            "US", 20,
            "SG", 30,
            "CA", 40,
            "CN", 50
    );

    /**
     * 可映射到中文文案包的主要语言代码。
     */
    private static final Set<String> CHINESE_LANGUAGE_CODES = Set.of("zh", "zh-cn", "zh-hans", "cmn", "yue");

    /**
     * ISO 字典公共查询服务。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 创建收银台国家地区配置应用服务。
     *
     * @param isoDictionaryService ISO 字典公共查询服务
     */
    public CheckoutCountryConfigApplicationService(IsoDictionaryService isoDictionaryService) {
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 查询付款人收银台可展示的国家地区配置。
     *
     * @return 国家地区配置列表
     */
    public List<CheckoutCountryConfigResponse> listCountries() {
        List<IsoCountryInfo> countries = isoDictionaryService.listCountries();
        return IntStream.range(0, countries.size())
                .mapToObj(index -> toResponse(countries.get(index), index))
                .sorted((left, right) -> {
                    int sortCompare = left.sortNo().compareTo(right.sortNo());
                    if (sortCompare != 0) {
                        return sortCompare;
                    }
                    return left.countryCode().compareTo(right.countryCode());
                })
                .toList();
    }

    /**
     * 转换单个 ISO 国家地区为收银台展示配置。
     *
     * @param country ISO 国家地区
     * @param index   原始列表索引，用于稳定兜底排序
     * @return 收银台展示配置
     */
    private CheckoutCountryConfigResponse toResponse(IsoCountryInfo country, int index) {
        String countryCode = normalizeCountryCode(country.alpha2());
        return new CheckoutCountryConfigResponse(
                countryCode,
                resolveCountryName(country),
                resolveCountryNameLocal(country),
                resolveFlagIconUrl(country.flagEmoji()),
                resolveDefaultLanguage(country.primaryLanguageCode()),
                SUPPORTED_LANGUAGES,
                COUNTRY_SORT_OVERRIDES.getOrDefault(countryCode, 1000 + index)
        );
    }

    /**
     * 解析英文展示名称。
     *
     * @param country ISO 国家地区
     * @return 英文展示名称
     */
    private String resolveCountryName(IsoCountryInfo country) {
        if (StringUtils.hasText(country.shortEnglishName())) {
            return country.shortEnglishName();
        }
        if (StringUtils.hasText(country.englishName())) {
            return country.englishName();
        }
        return normalizeCountryCode(country.alpha2());
    }

    /**
     * 解析本地化展示名称。
     *
     * @param country ISO 国家地区
     * @return 本地化展示名称
     */
    private String resolveCountryNameLocal(IsoCountryInfo country) {
        if (StringUtils.hasText(country.chineseName())) {
            return country.chineseName();
        }
        return resolveCountryName(country);
    }

    /**
     * 将 ISO 字典中的国旗 Emoji 包装为前端可直接展示的 SVG Data URL。
     *
     * @param flagEmoji 国家或地区国旗 Emoji
     * @return SVG Data URL；为空时由前端显示默认地球图标
     */
    private String resolveFlagIconUrl(String flagEmoji) {
        if (!StringUtils.hasText(flagEmoji)) {
            return "";
        }
        String escapedEmoji = flagEmoji.trim()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\">"
                + "<text x=\"12\" y=\"17\" text-anchor=\"middle\" font-size=\"18\">" + escapedEmoji + "</text>"
                + "</svg>";
        return "data:image/svg+xml," + URLEncoder.encode(svg, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 将基础语言代码映射到收银台轻量文案包语言。
     *
     * @param languageCode 基础字典主要语言代码
     * @return 收银台语言代码
     */
    private String resolveDefaultLanguage(String languageCode) {
        if (!StringUtils.hasText(languageCode)) {
            return DEFAULT_LANGUAGE;
        }
        String normalizedLanguage = languageCode.trim().toLowerCase(Locale.ROOT);
        if (CHINESE_LANGUAGE_CODES.contains(normalizedLanguage)) {
            return "zh-CN";
        }
        return DEFAULT_LANGUAGE;
    }

    /**
     * 标准化国家地区代码。
     *
     * @param countryCode 国家地区代码
     * @return 大写国家地区代码
     */
    private String normalizeCountryCode(String countryCode) {
        return StringUtils.hasText(countryCode) ? countryCode.trim().toUpperCase(Locale.ROOT) : "";
    }
}
