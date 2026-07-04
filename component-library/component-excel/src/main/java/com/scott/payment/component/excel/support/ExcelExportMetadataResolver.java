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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportMetadataResolver
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Excel Export Metadata Resolver，位于 component-library/component-excel 的支撑组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class ExcelExportMetadataResolver {

    /**
     * 解析导出类上的列定义。
     *
     * @param rowClass 导出行类型
     * @return 已排序列定义
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param rowClass 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
                column.align()
        );
    }
}
