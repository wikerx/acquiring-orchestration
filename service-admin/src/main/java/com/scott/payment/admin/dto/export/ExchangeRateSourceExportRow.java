package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 汇率源导出行模型，用于运营后台，定义 Excel 列及运营可见值，不承载数据库写入规则。
 */
@Data
public class ExchangeRateSourceExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.source.sourceCode", width = 18)
    private String sourceCode;

    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.source.sourceName", width = 24)
    private String sourceName;

    /**
     * 数据来源类型，用于区分手工维护、文件导入、外部抓取或系统初始化数据。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.source.sourceType", width = 16)
    private String sourceType;

    /**
     * 请求URL，表示当前内部调用、渠道调用或商户通知的目标地址。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.source.requestUrl", width = 36)
    private String requestUrl;

    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.source.defaultSource", width = 14)
    private String defaultSource;

    /**
     * 匹配优先级，数值越小越优先。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.source.priority", width = 12)
    private Integer priority;

    /**
     * 远程调用超时时间，单位为秒。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.source.timeoutSeconds", width = 16)
    private Integer timeoutSeconds;

    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.source.sourceStatus", width = 12)
    private String sourceStatus;

    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.source.lastFetchTime", width = 22)
    private LocalDateTime lastFetchTime;

    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.source.lastFetchStatus", width = 16)
    private String lastFetchStatus;

    /**
     * 收单支付备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
