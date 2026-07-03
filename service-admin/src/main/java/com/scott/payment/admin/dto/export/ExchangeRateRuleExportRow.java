package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率规则配置导出行对象。
 */
@Data
public class ExchangeRateRuleExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;

    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.sourceCode", width = 18)
    private String sourceCode;

    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;

    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.rule.rateField", width = 18)
    private String rateField;

    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.rule.adjustDirection", width = 16)
    private String adjustDirection;

    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.rule.adjustMethod", width = 16)
    private String adjustMethod;

    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.rule.adjustValue", width = 18)
    private BigDecimal adjustValue;

    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.rule.decimalScale", width = 14)
    private Integer decimalScale;

    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.rule.roundingMode", width = 18)
    private String roundingMode;

    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.source.priority", width = 12)
    private Integer priority;

    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.rule.effectiveStartTime", width = 22)
    private LocalDateTime effectiveStartTime;

    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.rule.effectiveEndTime", width = 22)
    private LocalDateTime effectiveEndTime;

    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.rule.ruleStatus", width = 12)
    private String ruleStatus;

    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;

    @ExcelExportColumn(order = 16, headerKey = "excel.exchange.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
