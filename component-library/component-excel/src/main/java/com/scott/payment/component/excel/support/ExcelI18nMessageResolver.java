package com.scott.payment.component.excel.support;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelI18nMessageResolver
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 导出国际化文案解析器
 * @status : create
 *
 * <p>统一从 Spring MessageSource 解析 Excel 导出所需的标题、表头、状态值和元信息文案，
 * 避免把国际化文本硬编码在 Java 代码中，方便与前端语言配置长期保持一致。</p>
 */
@Component
public class ExcelI18nMessageResolver {

    /**
     * Spring 国际化消息源。
     */
    private final MessageSource messageSource;

    /**
     * 创建 Excel 国际化解析器。
     *
     * @param messageSource Spring 国际化消息源
     */
    public ExcelI18nMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 解析消息文案。
     *
     * @param messageKey 消息 key
     * @param locale     当前语言
     * @return 解析结果
     */
    public String resolve(String messageKey, Locale locale) {
        if (messageKey == null || messageKey.isBlank()) {
            return "";
        }
        Locale targetLocale = locale == null ? LocaleContextHolder.getLocale() : locale;
        return messageSource.getMessage(messageKey, null, messageKey, targetLocale);
    }
}
