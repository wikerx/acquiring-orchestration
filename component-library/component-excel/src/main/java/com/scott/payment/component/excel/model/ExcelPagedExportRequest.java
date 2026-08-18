package com.scott.payment.component.excel.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelPagedExportRequest
 * @date : 2026-08-05 00:00
 * @email : scott_x@163.com
 * @description : 分页流式 Excel 导出请求，每次只在内存中保留一页数据，适用于不限制总行数的交易下载。
 * @status : create
 */
@Getter
@Builder
public class ExcelPagedExportRequest<T> {

    /** 下载文件名，不包含扩展名。 */
    private final String fileName;
    /** Sheet 名称。 */
    private final String sheetName;
    /** 报表标题国际化 key。 */
    private final String titleKey;
    /** 导出操作人。 */
    private final String operator;
    /** 导出时间。 */
    private final LocalDateTime exportTime;
    /** 导出语言。 */
    private final Locale locale;
    /** 查询条件摘要。 */
    private final String querySummary;
    /** 导出行类型。 */
    private final Class<T> rowClass;
    /** 每页最大行数，用于判断是否已读取最后一页。 */
    private final int pageSize;
    /** 从 1 开始的页号加载器，返回空集合表示导出结束。 */
    private final IntFunction<List<T>> pageLoader;
}
