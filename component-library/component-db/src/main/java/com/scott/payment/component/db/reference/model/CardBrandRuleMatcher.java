package com.scott.payment.component.db.reference.model;

/**
 * 支付与 OpenAPI 共用的公开 IIN 卡品牌兜底规则；只识别品牌，不伪造 BIN 区间归属。
 */
public final class CardBrandRuleMatcher {

    /**
     * 禁止实例化公开 IIN 卡品牌匹配工具类。
     */
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

    /**
     * 判断卡号数字是否匹配任一受控品牌前缀。
     *
     * @param digits 纯数字卡号或 BIN
     * @param prefixes 受控品牌前缀
     * @return 命中任一前缀时返回 true
     */
    private static boolean startsWithAny(String digits, String... prefixes) {
        for (String prefix : prefixes) {
            if (digits.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断固定长度数字前缀是否位于品牌规则闭区间。
     *
     * @param digits 纯数字卡号或 BIN
     * @param length 需要读取的前缀长度
     * @param start 区间起点
     * @param end 区间终点
     * @return 前缀位于闭区间时返回 true
     */
    private static boolean prefixInRange(String digits, int length, int start, int end) {
        if (digits.length() < length) {
            return false;
        }
        int prefix = Integer.parseInt(digits.substring(0, length));
        return prefix >= start && prefix <= end;
    }
}
