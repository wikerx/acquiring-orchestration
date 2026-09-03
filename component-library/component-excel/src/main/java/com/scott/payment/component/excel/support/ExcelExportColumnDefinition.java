package com.scott.payment.component.excel.support;

import org.apache.poi.ss.usermodel.HorizontalAlignment;

import java.lang.reflect.Field;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportColumnDefinition
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 导出列元数据
 * @status : create
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
