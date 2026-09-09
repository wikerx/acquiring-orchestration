package com.scott.payment.component.excel.support;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportMetadataResolver
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 导出元数据解析器
 * @status : create
 *
 * <p>统一解析导出对象上的列注解，确保各业务导出使用相同的列定义机制。</p>
 */
@Component
public class ExcelExportMetadataResolver {

    /**
     * 解析导出类上的列定义。
     *
     * @param rowClass 导出行类型
     * @return 已排序列定义
     */
    public List<ExcelExportColumnDefinition> resolveColumns(Class<?> rowClass) {
        return List.of(rowClass.getDeclaredFields()).stream()
                .filter(field -> field.isAnnotationPresent(ExcelExportColumn.class))
                .map(this::toDefinition)
                .sorted(Comparator.comparingInt(ExcelExportColumnDefinition::order))
                .toList();
    }

    /**
     * 将字段转换为列定义。
     *
     * @param field 字段
     * @return 列定义
     */
    private ExcelExportColumnDefinition toDefinition(Field field) {
        ExcelExportColumn column = field.getAnnotation(ExcelExportColumn.class);
        field.setAccessible(true);
        return new ExcelExportColumnDefinition(
                field,
                column.order(),
                column.headerKey(),
                column.width(),
                column.align(),
                column.forceText()
        );
    }
}
