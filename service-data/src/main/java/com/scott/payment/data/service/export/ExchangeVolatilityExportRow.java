package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端汇率波动 Excel 行模型，汇率和百分比保持权威服务的十进制字符串。
 */
@Data
public class ExchangeVolatilityExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.volatility.latestRate", width = 20)
    private String latestRate;
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.volatility.minimumRate", width = 20)
    private String minimumRate;
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.volatility.maximumRate", width = 20)
    private String maximumRate;
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.volatility.volatilityRate", width = 18)
    private String volatilityRate;
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.volatility.sampleCount", width = 14)
    private Integer sampleCount;
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.volatility.latestEffectiveTime", width = 22)
    private LocalDateTime latestEffectiveTime;
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.volatility.alertStatus", width = 16)
    private String alertStatus;
}
