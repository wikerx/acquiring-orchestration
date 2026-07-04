package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateSourceExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Exchange Rate Source Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ExchangeRateSourceExportRow {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.source.sourceCode", width = 18)
    private String sourceCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.source.sourceName", width = 24)
    private String sourceName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.source.sourceType", width = 16)
    private String sourceType;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.source.requestUrl", width = 36)
    private String requestUrl;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.source.defaultSource", width = 14)
    private String defaultSource;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.source.priority", width = 12)
    private Integer priority;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.source.timeoutSeconds", width = 16)
    private Integer timeoutSeconds;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.source.sourceStatus", width = 12)
    private String sourceStatus;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.source.lastFetchTime", width = 22)
    private LocalDateTime lastFetchTime;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.source.lastFetchStatus", width = 16)
    private String lastFetchStatus;

    /**
     * 收单支付备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
