package com.scott.payment.component.db.auth.support;

/** Merchant-level locale values supported by email templates and the merchant portal. */
public final class MerchantLocaleSupport {

    public static final String CHINESE = "zh-CN";
    public static final String ENGLISH = "en-US";

    private MerchantLocaleSupport() {
    }

    public static String normalize(String locale) {
        return ENGLISH.equalsIgnoreCase(locale == null ? "" : locale.trim()) ? ENGLISH : CHINESE;
    }
}
