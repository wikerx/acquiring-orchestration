package com.scott.payment.component.db.auth.support;

/**
 * Merchant-level locale values supported by email templates and the merchant portal.
 */
public final class MerchantLocaleSupport {

    /**
     * 平台支持的简体中文语言区域编码。
     */
    public static final String CHINESE = "zh-CN";

    /**
     * 平台支持的美国英语语言区域编码。
     */
    public static final String ENGLISH = "en-US";

    /**
     * 禁止实例化商户语言区域工具类。
     */
    private MerchantLocaleSupport() {
    }


    /**
     * 将商户语言区域归一为平台支持的中文或英文值，未知值回退英文。
     *
     * @param locale 商户语言区域配置
     * @return {@value #CHINESE} 或 {@value #ENGLISH}
     */
    public static String normalize(String locale) {
        return ENGLISH.equalsIgnoreCase(locale == null ? "" : locale.trim()) ? ENGLISH : CHINESE;
    }
}
