package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/** 汇率波动预警事件导出行，保留窗口证据和处理审计。 */
@Data
public class ExchangeRateAlertEventExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.alertEvent.eventNo", width = 28)
    private String eventNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.alertRule.ruleCode", width = 28)
    private String ruleCode;
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.alertRule.alertLevel", width = 16)
    private String alertLevel;
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.alertEvent.eventStatus", width = 16)
    private String eventStatus;
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.volatility.volatilityRate", width = 18)
    private String volatilityRate;
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.alertEvent.maximumVolatilityRate", width = 20)
    private String maximumVolatilityRate;
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.alertRule.triggerThreshold", width = 18)
    private String triggerThreshold;
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.volatility.minimumRate", width = 20)
    private String minimumRate;
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.alertEvent.minimumRateSource", width = 20)
    private String minimumRateSource;
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.volatility.maximumRate", width = 20)
    private String maximumRate;
    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.alertEvent.maximumRateSource", width = 20)
    private String maximumRateSource;
    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.volatility.latestRate", width = 20)
    private String latestRate;
    @ExcelExportColumn(order = 16, headerKey = "excel.exchange.alertEvent.latestRateSource", width = 20)
    private String latestRateSource;
    @ExcelExportColumn(order = 17, headerKey = "excel.exchange.volatility.sampleCount", width = 14)
    private Integer sampleCount;
    @ExcelExportColumn(order = 18, headerKey = "excel.exchange.alertEvent.firstTriggerTime", width = 22)
    private LocalDateTime firstTriggerTime;
    @ExcelExportColumn(order = 19, headerKey = "excel.exchange.alertEvent.lastDetectedTime", width = 22)
    private LocalDateTime lastDetectedTime;
    @ExcelExportColumn(order = 20, headerKey = "excel.exchange.alertEvent.acknowledgedBy", width = 18)
    private String acknowledgedBy;
    @ExcelExportColumn(order = 21, headerKey = "excel.exchange.alertEvent.resolvedTime", width = 22)
    private LocalDateTime resolvedTime;
}
