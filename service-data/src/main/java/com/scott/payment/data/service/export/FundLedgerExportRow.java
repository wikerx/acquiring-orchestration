package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成管理端余额流水 Excel 的独立行模型，不改变金额、余额或币种语义。
 */
@Data
public class FundLedgerExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.fund.ledgerNo", width = 26)
    private String ledgerNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.fund.accountNo", width = 24)
    private String accountNo;
    @ExcelExportColumn(order = 3, headerKey = "excel.fund.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 4, headerKey = "excel.fund.merchantName", width = 28)
    private String merchantName;
    @ExcelExportColumn(order = 5, headerKey = "excel.fund.businessType", width = 18)
    private String businessType;
    @ExcelExportColumn(order = 6, headerKey = "excel.fund.summary", width = 36)
    private String summary;
    @ExcelExportColumn(order = 7, headerKey = "excel.fund.businessNo", width = 28)
    private String businessNo;
    @ExcelExportColumn(order = 8, headerKey = "excel.fund.balanceType", width = 16)
    private String balanceType;
    @ExcelExportColumn(order = 9, headerKey = "excel.fund.direction", width = 12)
    private String direction;
    @ExcelExportColumn(order = 10, headerKey = "excel.fund.amount", width = 20)
    private BigDecimal amount;
    @ExcelExportColumn(order = 11, headerKey = "excel.fund.currency", width = 12)
    private String currency;
    @ExcelExportColumn(order = 12, headerKey = "excel.fund.balanceBefore", width = 20)
    private BigDecimal balanceBefore;
    @ExcelExportColumn(order = 13, headerKey = "excel.fund.balanceAfter", width = 20)
    private BigDecimal balanceAfter;
    @ExcelExportColumn(order = 14, headerKey = "excel.fund.accountSequence", width = 18)
    private Long accountSequence;
    @ExcelExportColumn(order = 15, headerKey = "excel.fund.operator", width = 18)
    private String operatorName;
    @ExcelExportColumn(order = 16, headerKey = "excel.fund.reviewer", width = 18)
    private String reviewerName;
    @ExcelExportColumn(order = 17, headerKey = "excel.fund.operationReason", width = 36)
    private String operationReason;
    @ExcelExportColumn(order = 18, headerKey = "excel.fund.reviewComment", width = 36)
    private String reviewComment;
    @ExcelExportColumn(order = 19, headerKey = "excel.fund.businessTime", width = 22)
    private LocalDateTime businessTime;
    @ExcelExportColumn(order = 20, headerKey = "excel.fund.postedTime", width = 22)
    private LocalDateTime postedTime;
    @ExcelExportColumn(order = 21, headerKey = "excel.fund.requestId", width = 26)
    private String requestId;
    @ExcelExportColumn(order = 22, headerKey = "excel.fund.idempotencyKey", width = 34)
    private String idempotencyKey;
}
