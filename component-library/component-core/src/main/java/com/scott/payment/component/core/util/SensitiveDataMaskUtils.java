package com.scott.payment.component.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SensitiveDataMaskUtils
 * @date : 2026-05-28 16:22
 * @email : scott_x@163.com
 * @description : 敏感数据脱敏工具
 * @status : create
 */
public final class SensitiveDataMaskUtils {

    /**
     * 卡号字段脱敏正则，匹配 cardNo 或 pan，并保留前 6 位和后 4 位用于排查与对账。
     */
    private static final Pattern CARD_FIELD_PATTERN = Pattern.compile(
            "(\"(?:cardNo|pan)\"\\s*:\\s*\")([0-9]{6})([0-9]{1,9})([0-9]{4})(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 安全码字段脱敏正则，匹配 securityCode、cvv、cvc，日志中统一替换为星号。
     */
    private static final Pattern SECURITY_CODE_PATTERN = Pattern.compile(
            "(\"(?:securityCode|cvv|cvc)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 3DS 持卡人认证值脱敏正则，CAVV 属于交易认证敏感数据，日志中统一替换为星号。
     */
    private static final Pattern CAVV_PATTERN = Pattern.compile(
            "(\"(?:cavv)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 工具类不允许实例化。
     */
    private SensitiveDataMaskUtils() {
    }

    /**
     * 对 JSON 文本中的敏感字段执行统一脱敏。
     *
     * @param json 原始 JSON 文本
     * @return 脱敏后的 JSON 文本
     */
    public static String maskJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String masked = maskCardNo(json);
        masked = SECURITY_CODE_PATTERN.matcher(masked).replaceAll("$1***$3");
        return CAVV_PATTERN.matcher(masked).replaceAll("$1***$3");
    }

    /**
     * 对卡号字段执行脱敏。
     *
     * @param value 原始文本
     * @return 脱敏后的文本
     */
    public static String maskCardNo(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return CARD_FIELD_PATTERN.matcher(value).replaceAll(matchResult -> Matcher.quoteReplacement(
                matchResult.group(1)
                        + matchResult.group(2)
                        + "******"
                        + matchResult.group(4)
                        + matchResult.group(5)
        ));
    }
}
