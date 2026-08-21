package com.scott.payment.component.db.dictionary.support;

import org.springframework.util.StringUtils;

/** 公共字典下拉缓存业务键规范化工具。 */
public final class DictionaryOptionCacheKey {

    private DictionaryOptionCacheKey() {
    }

    /**
     * 生成字典类型和语言唯一确定的缓存键。
     *
     * @param dictType 字典类型
     * @param locale 语言区域
     * @return {@code dictType:locale} 格式的业务键
     */
    public static String of(String dictType, String locale) {
        if (!StringUtils.hasText(dictType) || !StringUtils.hasText(locale)) {
            throw new IllegalArgumentException("Dictionary type and locale are required");
        }
        return dictType.trim() + ":" + locale.trim();
    }
}
