package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端汇率预警规则 Excel 行模型，阈值保持业务定义的百分数字符串。
 */
@Data
public class ExchangeAlertRuleExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.alertRule.ruleCode", width = 28)
    private String ruleCode;
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.alertRule.ruleName", width = 32)
    private String ruleName;
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.alertRule.windowMinutes", width = 18)
    private Integer windowMinutes;
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.alertRule.triggerThreshold", width = 18)
    private String triggerThreshold;
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.alertRule.recoveryThreshold", width = 18)
    private String recoveryThreshold;
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.alertRule.recoveryConsecutiveCount", width = 20)
    private Integer recoveryConsecutiveCount;
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.alertRule.minimumSampleCount", width = 18)
    private Integer minimumSampleCount;
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.alertRule.alertLevel", width = 16)
    private String alertLevel;
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.alertRule.systemMandatory", width = 16)
    private String systemMandatory;
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.alertRule.ruleStatus", width = 14)
    private String ruleStatus;
    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.common.remark", width = 32)
    private String remark;
    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
