package com.scott.payment.component.core.iso;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyResolver
 * @date : 2026-06-02 15:55
 * @email : scott_x@163.com
 * @description : ISO 4217 币种识别与金额辅币位工具
 * @status : create
 */
public final class IsoCurrencyResolver {

    /**
     * 简体中文显示名称使用的 Locale。
     */
    private static final Locale ZH_CN = Locale.SIMPLIFIED_CHINESE;

    /**
     * 英文显示名称使用的 Locale。
     */
    private static final Locale EN = Locale.ENGLISH;

    /**
     * 标准化后的币种索引，覆盖 ISO 4217 三字母码、三数字码、英文名和中文名。
     */
    private static final Map<String, IsoCurrencyInfo> CURRENCY_INDEX = buildCurrencyIndex();

    private IsoCurrencyResolver() {
    }

    /**
     * 根据币种代码或名称识别 ISO 4217 币种。
     *
     * @param value 三字母代码、三数字代码、英文名称或中文名称
     * @return 币种信息
     */
    public static Optional<IsoCurrencyInfo> resolve(String value) {
        if (!hasText(value)) {
            return Optional.empty();
        }
        return Optional.ofNullable(CURRENCY_INDEX.get(normalize(value)));
    }

    /**
     * 校验金额小数位是否符合币种默认辅币位。
     *
     * @param amount   交易金额，禁止使用 double/float
     * @param currency 币种信息
     * @return true 表示金额小数位不超过币种默认辅币位
     */
    public static boolean isValidFraction(BigDecimal amount, IsoCurrencyInfo currency) {
        if (amount == null || currency == null || currency.defaultFractionDigits() < 0) {
            return false;
        }
        return amount.stripTrailingZeros().scale() <= currency.defaultFractionDigits();
    }

    /**
     * 将主币单位金额转换为最小辅币单位。
     * <p>
     * 例如 USD 12.34 转换为 1234，JPY 12 转换为 12。金额小数位超过币种定义时直接抛出异常，避免支付金额被
     * 四舍五入后产生资金误差。
     *
     * @param amount   主币单位金额
     * @param currency 币种信息
     * @return 最小辅币单位金额
     */
    public static long toMinorUnit(BigDecimal amount, IsoCurrencyInfo currency) {
        if (!isValidFraction(amount, currency)) {
            throw new IllegalArgumentException("amount fraction digits exceed currency minor unit");
        }
        return amount
                .multiply(BigDecimal.valueOf(currency.minorUnitMultiplier()))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    /**
     * 查询当前币种索引。
     *
     * @return 标准化查询值到币种信息的映射
     */
    public static Map<String, IsoCurrencyInfo> indexSnapshot() {
        return CURRENCY_INDEX;
    }

    /**
     * 构建 ISO 4217 币种索引。
     *
     * @return 标准化查询值到币种信息的映射
     */
    private static Map<String, IsoCurrencyInfo> buildCurrencyIndex() {
        Map<String, IsoCurrencyInfo> index = new LinkedHashMap<>();
        Currency.getAvailableCurrencies().forEach(currency -> {
            IsoCurrencyInfo info = toCurrencyInfo(currency);
            index.put(normalize(info.alphabeticCode()), info);
            index.put(normalize(info.numericCode()), info);
            index.put(normalize(info.englishName()), info);
            index.put(normalize(info.chineseName()), info);
        });
        return Map.copyOf(index);
    }

    /**
     * 将 JDK Currency 转换为支付框架币种信息。
     *
     * @param currency JDK 币种对象
     * @return ISO 币种信息
     */
    private static IsoCurrencyInfo toCurrencyInfo(Currency currency) {
        int fractionDigits = currency.getDefaultFractionDigits();
        long multiplier = fractionDigits < 0 ? 0L : BigDecimal.TEN.pow(fractionDigits).longValueExact();
        return new IsoCurrencyInfo(
                currency.getCurrencyCode(),
                String.format("%03d", currency.getNumericCode()),
                currency.getDisplayName(EN),
                currency.getDisplayName(ZH_CN),
                fractionDigits,
                multiplier
        );
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
