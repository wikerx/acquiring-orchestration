package com.scott.payment.component.db.email.support;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailTemplateCacheKey
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 已启用邮件模板缓存业务键规范化工具，统一模板编码大小写与语言组合格式
 * @status : create
 */
public final class EmailTemplateCacheKey {

    private EmailTemplateCacheKey() {
    }

    /**
     * 生成模板编码和语言唯一确定的缓存键。
     *
     * @param templateCode 模板编码
     * @param localeCode 语言区域
     * @return {@code TEMPLATE_CODE:locale} 格式的业务键
     */
    public static String of(String templateCode, String localeCode) {
        if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(localeCode)) {
            throw new IllegalArgumentException("Email template code and locale are required");
        }
        return templateCode.trim().toUpperCase(Locale.ROOT) + ":" + localeCode.trim();
    }
}
