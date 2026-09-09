package com.scott.payment.component.excel.annotation;

import org.apache.poi.ss.usermodel.HorizontalAlignment;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportColumn
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 导出列定义注解
 * @status : create
 *
 * <p>用于声明导出列顺序、国际化表头 key、字段对齐方式和建议列宽，
 * 避免直接在业务导出对象中硬编码中文表头。</p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelExportColumn {

    /**
     * 列顺序，数字越小越靠前。
     *
     * @return 排序值
     */
    int order();

    /**
     * 表头国际化 key。
     *
     * @return 国际化 key
     */
    String headerKey();

    /**
     * 建议列宽，单位为字符宽度。
     *
     * @return 建议列宽
     */
    int width() default 18;

    /**
     * 单元格水平对齐方式。
     *
     * @return 对齐方式
     */
    HorizontalAlignment align() default HorizontalAlignment.LEFT;

    /**
     * 是否强制按文本单元格格式写出，适用于超长交易号等不可计算标识符。
     *
     * @return true 表示使用 Excel 文本格式
     */
    boolean forceText() default false;
}
