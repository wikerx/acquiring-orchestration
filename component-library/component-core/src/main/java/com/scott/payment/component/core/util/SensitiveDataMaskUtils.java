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

    private static final Pattern CARD_FIELD_PATTERN = Pattern.compile(
            "(\"(?:cardNo|pan)\"\\s*:\\s*\")([0-9]{6})([0-9]{1,9})([0-9]{4})(\")",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SECURITY_CODE_PATTERN = Pattern.compile(
            "(\"(?:securityCode|cvv|cvc)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    private SensitiveDataMaskUtils() {
    }

    public static String maskJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String masked = maskCardNo(json);
        return SECURITY_CODE_PATTERN.matcher(masked).replaceAll("$1***$3");
    }

    public static String maskCardNo(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = CARD_FIELD_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1)
                    + matcher.group(2)
                    + "******"
                    + matcher.group(4)
                    + matcher.group(5);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
