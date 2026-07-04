package com.scott.payment.component.excel.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportRequest
 * @date : 2026-06-19 23:35
 * @email : scott_x@163.com
 * @description : Excel 导出请求模型
 * @status : create
 *
 * <p>封装统一导出所需的文件名、标题、导出人、查询条件和导出数据，
 * 让业务层只关注数据准备，不直接拼装 EasyExcel 细节。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Excel Export 请求对象，位于 component-library/component-excel 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Getter
@Builder
public class ExcelExportRequest<T> {

    /**
     * 下载文件名，不包含扩展名。
     */
    private final String fileName;

    /**
     * Sheet 名称。
     */
    private final String sheetName;

    /**
     * 报表标题国际化 key。
     */
    private final String titleKey;

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
     * 导出行类型。
     */
    private final Class<T> rowClass;

    /**
     * 导出数据。
     */
    private final List<T> dataList;
}
