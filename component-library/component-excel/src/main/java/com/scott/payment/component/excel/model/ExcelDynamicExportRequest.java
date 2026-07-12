package com.scott.payment.component.excel.model;

import com.scott.payment.component.excel.support.ExcelDynamicColumnDefinition;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelDynamicExportRequest
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 动态列 Excel 导出请求，服务于风控等列结构随功能变化的管理端导出场景。
 * @status : create
 */
@Getter
@Builder
public class ExcelDynamicExportRequest {

    /**
     * 下载文件名，不包含扩展名。
     */
    private final String fileName;

    /**
     * Sheet 名称。
     */
    private final String sheetName;

    /**
     * 报表标题，调用方负责按当前语言解析。
     */
    private final String title;

    /**
     * 导出操作人。
     */
    private final String operator;

    /**
     * 导出时间。
     */
    private final LocalDateTime exportTime;

    /**
     * 导出语言。
     */
    private final Locale locale;

    /**
     * 查询条件摘要。
     */
    private final String querySummary;

    /**
     * 动态列定义，顺序即导出顺序。
     */
    private final List<ExcelDynamicColumnDefinition> columns;

    /**
     * 动态行数据，key 与列定义 key 对应。
     */
    private final List<Map<String, Object>> dataList;
}
