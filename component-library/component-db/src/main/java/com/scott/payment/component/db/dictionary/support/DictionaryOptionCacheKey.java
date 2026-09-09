package com.scott.payment.component.db.dictionary.support;

import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DictionaryOptionCacheKey
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 公共字典下拉缓存业务键规范化工具。
 * @status : create
 */
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
