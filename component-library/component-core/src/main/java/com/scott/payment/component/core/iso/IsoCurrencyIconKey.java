package com.scott.payment.component.core.iso;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 币种展示图标键规范，拒绝外部 URL、HTML 和未受控资源路径。
 */
public final class IsoCurrencyIconKey {

    private static final Pattern FLAG_KEY = Pattern.compile("^FLAG:([A-Z]{2})$");
    private static final Pattern CURRENCY_KEY = Pattern.compile("^CURRENCY:([A-Z0-9]{3,12})$");

    private IsoCurrencyIconKey() {
    }

    /**
     * 规范化并校验展示图标键。
     *
     * @param alphabeticCode 当前币种代码
     * @param iconKey 输入图标键；空值表示使用前端安全回退头像
     * @return 规范化图标键或 null
     */
    public static String normalize(String alphabeticCode, String iconKey) {
        if (!hasText(iconKey)) {
            return null;
        }
        String normalized = iconKey.trim().toUpperCase(Locale.ROOT);
        Matcher flagMatcher = FLAG_KEY.matcher(normalized);
        if (flagMatcher.matches()) {
            return "flag:" + flagMatcher.group(1);
        }
        Matcher currencyMatcher = CURRENCY_KEY.matcher(normalized);
        if (currencyMatcher.matches()) {
            String iconCurrency = currencyMatcher.group(1);
            String normalizedCurrency = hasText(alphabeticCode)
                    ? alphabeticCode.trim().toUpperCase(Locale.ROOT)
                    : null;
            if (normalizedCurrency != null && !normalizedCurrency.equals(iconCurrency)) {
                throw new IllegalArgumentException("currency icon key must match alpha3Code");
            }
            return "currency:" + iconCurrency;
        }
        throw new IllegalArgumentException("currency icon key must use flag:XX or currency:CODE");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
