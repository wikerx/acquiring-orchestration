package com.scott.payment.component.db.reference.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBrandRuleMatcher
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 支付与 OpenAPI 共用的公开 IIN 卡品牌兜底规则；只识别品牌，不伪造 BIN 区间归属。
 * @status : create
 */
public final class CardBrandRuleMatcher {

    private CardBrandRuleMatcher() {
    }

    /**
     * 在 BIN 基础数据未命中时，根据纯数字前缀识别平台标准卡品牌。
     * 更具体的 Discover、UnionPay、Mastercard 规则优先于 Maestro。
     *
     * @param cardDigits 纯数字 BIN 或卡号；本方法不记录和保存输入
     * @return 平台标准卡品牌，无法识别时返回 {@code UNKNOWN}
     */
    public static String resolve(String cardDigits) {
        String digits = cardDigits == null ? "" : cardDigits.trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return "UNKNOWN";
        }
        if (digits.startsWith("4")) {
            return "VISA";
        }
        if (startsWithAny(digits, "34", "37")) {
            return "AMEX";
        }
        if (startsWithAny(digits, "1800", "2131") || prefixInRange(digits, 4, 3528, 3589)) {
            return "JCB";
        }
        if (startsWithAny(digits, "36", "38") || prefixInRange(digits, 3, 300, 305)) {
            return "DINERS_CLUB";
        }
        if (digits.startsWith("6011")
                || digits.startsWith("65")
                || prefixInRange(digits, 3, 644, 649)
                || prefixInRange(digits, 6, 622126, 622925)) {
            return "DISCOVER";
        }
        if (digits.startsWith("62")) {
            return "UNIONPAY";
        }
        if (prefixInRange(digits, 2, 51, 55) || prefixInRange(digits, 4, 2221, 2720)) {
            return "MASTERCARD";
        }
        if (digits.length() >= 2 && (digits.startsWith("50") || prefixInRange(digits, 2, 56, 69))) {
            return "MAESTRO";
        }
        return "UNKNOWN";
    }

    private static boolean startsWithAny(String digits, String... prefixes) {
        for (String prefix : prefixes) {
            if (digits.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean prefixInRange(String digits, int length, int start, int end) {
        if (digits.length() < length) {
            return false;
        }
        int prefix = Integer.parseInt(digits.substring(0, length));
        return prefix >= start && prefix <= end;
    }
}
