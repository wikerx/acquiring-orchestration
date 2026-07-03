package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 汇率源管理导出行对象。
 */
@Data
public class ExchangeRateSourceExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.source.sourceCode", width = 18)
    private String sourceCode;

    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.source.sourceName", width = 24)
    private String sourceName;

    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.source.sourceType", width = 16)
    private String sourceType;

    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.source.requestUrl", width = 36)
    private String requestUrl;

    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.source.defaultSource", width = 14)
    private String defaultSource;

    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.source.priority", width = 12)
    private Integer priority;

    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.source.timeoutSeconds", width = 16)
    private Integer timeoutSeconds;

    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.source.sourceStatus", width = 12)
    private String sourceStatus;

    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.source.lastFetchTime", width = 22)
    private LocalDateTime lastFetchTime;

    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.source.lastFetchStatus", width = 16)
    private String lastFetchStatus;

    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;

    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;

    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
