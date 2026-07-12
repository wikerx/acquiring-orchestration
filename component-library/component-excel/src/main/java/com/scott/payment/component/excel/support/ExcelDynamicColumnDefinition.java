package com.scott.payment.component.excel.support;

import org.apache.poi.ss.usermodel.HorizontalAlignment;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelDynamicColumnDefinition
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 动态 Excel 导出列定义，用于不适合通过固定 DTO 注解声明列结构的管理端导出。
 * @status : create
 */
public record ExcelDynamicColumnDefinition(
        String key,
        String header,
        int width,
        HorizontalAlignment align
) {
}
