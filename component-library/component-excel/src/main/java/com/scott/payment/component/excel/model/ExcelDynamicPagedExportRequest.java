package com.scott.payment.component.excel.model;

import com.scott.payment.component.excel.support.ExcelDynamicColumnDefinition;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * 动态列分页 Excel 导出请求，每次只保留一页 Map 行，适用于列结构随业务功能变化的异步导出。
 */
@Getter
@Builder
public class ExcelDynamicPagedExportRequest {

    /** 下载文件名，不包含扩展名。 */
    private final String fileName;
    /** Sheet 名称。 */
    private final String sheetName;
    /** 已按当前语言解析的报表标题。 */
    private final String title;
    /** 导出操作人。 */
    private final String operator;
    /** 导出时间。 */
    private final LocalDateTime exportTime;
    /** 导出语言。 */
    private final Locale locale;
    /** 查询条件摘要。 */
    private final String querySummary;
    /** 动态列定义。 */
    private final List<ExcelDynamicColumnDefinition> columns;
    /** 每页最大行数。 */
    private final int pageSize;
    /** 从 1 开始的页号加载器。 */
    private final IntFunction<List<Map<String, Object>>> pageLoader;
}
