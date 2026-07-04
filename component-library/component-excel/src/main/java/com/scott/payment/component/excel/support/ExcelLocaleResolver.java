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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelLocaleResolver
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Excel Locale Resolver，位于 component-library/component-excel 的支撑组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class ExcelLocaleResolver {

    /**
     * 解析当前请求语言。
     *
     * @return 当前语言环境
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public Locale resolveCurrentLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale == null ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
