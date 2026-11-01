package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率规则导出行模型，用于运营后台，定义 Excel 列及运营可见值，不承载数据库写入规则。
 */
@Data
public class ExchangeRateRuleExportRow {

    /**
     * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;

    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.sourceCode", width = 18)
    private String sourceCode;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;

    /**
     * 收单支付币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
     */
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

    /**
     * 匹配优先级，数值越小越优先。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.source.priority", width = 12)
    private Integer priority;

    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.rule.effectiveStartTime", width = 22)
    private LocalDateTime effectiveStartTime;

    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.rule.effectiveEndTime", width = 22)
    private LocalDateTime effectiveEndTime;

    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.rule.ruleStatus", width = 12)
    private String ruleStatus;

    /**
     * 收单支付备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;

    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.exchange.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
