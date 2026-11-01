package com.scott.payment.component.excel.support;

import org.apache.poi.ss.usermodel.HorizontalAlignment;

/**
 * 动态 Excel 导出列定义，用于不适合通过固定 DTO 注解声明列结构的管理端导出。
 */
public record ExcelDynamicColumnDefinition(
        String key,
        String header,
        int width,
        HorizontalAlignment align
) {
}
