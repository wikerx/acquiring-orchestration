package com.scott.payment.payment.service.impl;

/** 数据库 BIN 未命中时，按平台卡品牌标准识别公开 IIN 前缀。 */
final class PaymentCardBrandRuleMatcher {

    private PaymentCardBrandRuleMatcher() {
    }

    static String resolve(String digits) {
        if (digits == null || digits.isEmpty()) {
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
