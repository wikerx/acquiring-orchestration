package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Data 生成商户结算汇总 Excel 的安全行模型，金额直接来自不可变结算聚合结果。
 */
@Data
public class MerchantSettlementSummaryExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.paymentType", width = 16)
    private String paymentType;
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.paymentMethod", width = 18)
    private String paymentMethod;
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.transactionType", width = 18)
    private String transactionType;
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.resultItemType", width = 20)
    private String resultItemType;
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.feeCategory", width = 18)
    private String feeCategory;
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.direction", width = 12)
    private String direction;
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.sourceCurrency", width = 14)
    private String sourceCurrency;
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.sourceAmount", width = 20)
    private BigDecimal sourceAmount;
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.targetCurrency", width = 14)
    private String targetCurrency;
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.targetAmount", width = 20)
    private BigDecimal targetAmount;
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.transactionCount", width = 14)
    private Long transactionCount;
}
