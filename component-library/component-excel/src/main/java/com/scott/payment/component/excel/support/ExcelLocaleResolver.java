package com.scott.payment.component.excel.support;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelLocaleResolver
 * @date : 2026-06-20 00:20
 * @email : scott_x@163.com
 * @description : Excel 导出语言解析器
 * @status : create
 *
 * <p>统一从当前请求上下文中解析导出语言，优先复用前端传入的 Accept-Language，
 * 没有显式语言时回退到系统默认中文，避免各应用服务手工写死 Locale。</p>
 */
@Component
public class ExcelLocaleResolver {

    /**
     * 解析当前请求语言。
     *
     * @return 当前语言环境
     */
    public Locale resolveCurrentLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale == null ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
