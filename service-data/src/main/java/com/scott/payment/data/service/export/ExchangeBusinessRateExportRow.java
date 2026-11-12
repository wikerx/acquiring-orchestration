package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成管理端业务汇率 Excel 的独立行模型，不重新计算原始汇率或最终汇率。
 */
@Data
public class ExchangeBusinessRateExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.exchange.common.rateType", width = 18)
    private String rateType;
    @ExcelExportColumn(order = 2, headerKey = "excel.exchange.common.sourceCode", width = 18)
    private String sourceCode;
    @ExcelExportColumn(order = 3, headerKey = "excel.exchange.common.baseCurrency", width = 14)
    private String baseCurrency;
    @ExcelExportColumn(order = 4, headerKey = "excel.exchange.common.quoteCurrency", width = 14)
    private String quoteCurrency;
    @ExcelExportColumn(order = 5, headerKey = "excel.exchange.business.originalRate", width = 18)
    private BigDecimal originalRate;
    @ExcelExportColumn(order = 6, headerKey = "excel.exchange.business.finalRate", width = 18)
    private BigDecimal finalRate;
    @ExcelExportColumn(order = 7, headerKey = "excel.exchange.common.effectiveTime", width = 22)
    private LocalDateTime effectiveTime;
    @ExcelExportColumn(order = 8, headerKey = "excel.exchange.business.expireTime", width = 22)
    private LocalDateTime expireTime;
    @ExcelExportColumn(order = 9, headerKey = "excel.exchange.business.generateMethod", width = 16)
    private String generateMethod;
    @ExcelExportColumn(order = 10, headerKey = "excel.exchange.common.rateStatus", width = 14)
    private String rateStatus;
    @ExcelExportColumn(order = 11, headerKey = "excel.exchange.business.rawRateId", width = 14)
    private Long rawRateId;
    @ExcelExportColumn(order = 12, headerKey = "excel.exchange.business.ruleId", width = 14)
    private Long ruleId;
    @ExcelExportColumn(order = 13, headerKey = "excel.exchange.business.adjustDescription", width = 40)
    private String adjustDescription;
    @ExcelExportColumn(order = 14, headerKey = "excel.exchange.common.remark", width = 30)
    private String remark;
    @ExcelExportColumn(order = 15, headerKey = "excel.exchange.common.createTime", width = 22)
    private LocalDateTime createTime;
}
