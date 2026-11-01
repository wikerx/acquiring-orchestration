package com.scott.payment.component.excel.support;

import org.apache.poi.ss.usermodel.HorizontalAlignment;

import java.lang.reflect.Field;

/**
 * Excel 导出列元数据
 */
public record ExcelExportColumnDefinition(
        Field field,
        int order,
        String headerKey,
        int width,
        HorizontalAlignment align,
        boolean forceText
) {
}
