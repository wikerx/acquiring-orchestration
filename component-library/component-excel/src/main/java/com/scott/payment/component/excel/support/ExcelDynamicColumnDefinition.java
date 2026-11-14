package com.scott.payment.component.excel.support;

import org.apache.poi.ss.usermodel.HorizontalAlignment;

/**
 * 动态 Excel 导出列定义，用于不适合通过固定 DTO 注解声明列结构的管理端导出。
 */
public record ExcelDynamicColumnDefinition(
        String key,
        String header,
        int width,
        HorizontalAlignment align,
        boolean forceText
) {

    /**
     * 创建普通动态列，保持既有调用方默认行为。
     *
     * @param key 数据字段键
     * @param header 表头文案
     * @param width 列宽
     * @param align 对齐方式
     */
    public ExcelDynamicColumnDefinition(String key,
                                        String header,
                                        int width,
                                        HorizontalAlignment align) {
        this(key, header, width, align, false);
    }
}
