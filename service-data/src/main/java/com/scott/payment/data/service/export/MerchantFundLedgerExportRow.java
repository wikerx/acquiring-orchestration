package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成商户余额流水 Excel 的安全行模型，不包含内部幂等键和平台账户字段。
 */
@Data
public class MerchantFundLedgerExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fund.ledgerNo", width = 26, forceText = true)
    private String ledgerNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.fund.businessType", width = 18)
    private String businessType;
    @ExcelExportColumn(order = 3, headerKey = "excel.fund.summary", width = 36)
    private String summary;
    @ExcelExportColumn(order = 4, headerKey = "excel.fund.businessNo", width = 28, forceText = true)
    private String businessNo;
    @ExcelExportColumn(order = 5, headerKey = "excel.fund.balanceType", width = 16)
    private String balanceType;
    @ExcelExportColumn(order = 6, headerKey = "excel.fund.direction", width = 12)
    private String direction;
    @ExcelExportColumn(order = 7, headerKey = "excel.fund.amount", width = 20, forceText = true)
    private BigDecimal amount;
    @ExcelExportColumn(order = 8, headerKey = "excel.fund.currency", width = 12)
    private String currency;
    @ExcelExportColumn(order = 9, headerKey = "excel.fund.balanceBefore", width = 20, forceText = true)
    private BigDecimal balanceBefore;
    @ExcelExportColumn(order = 10, headerKey = "excel.fund.balanceAfter", width = 20, forceText = true)
    private BigDecimal balanceAfter;
    @ExcelExportColumn(order = 11, headerKey = "excel.fund.operator", width = 18)
    private String operatorName;
    @ExcelExportColumn(order = 12, headerKey = "excel.fund.reviewer", width = 18)
    private String reviewerName;
    @ExcelExportColumn(order = 13, headerKey = "excel.fund.postedTime", width = 22)
    private LocalDateTime postedTime;
}
