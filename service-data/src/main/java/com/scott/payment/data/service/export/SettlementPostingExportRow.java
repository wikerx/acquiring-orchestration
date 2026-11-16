package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成管理端结算入账流水 Excel 的独立行模型，保留余额和冲正审计事实。
 */
@Data
public class SettlementPostingExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.ledgerNo", width = 28)
    private String ledgerNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.accountId", width = 18)
    private Long accountId;
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.businessType", width = 22)
    private String businessType;
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.direction", width = 12)
    private String direction;
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.amount", width = 20)
    private BigDecimal amount;
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.currency", width = 12)
    private String currency;
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.balanceBefore", width = 20)
    private BigDecimal balanceBefore;
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.balanceAfter", width = 20)
    private BigDecimal balanceAfter;
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.accountSequence", width = 18)
    private Long accountSequence;
    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.operationMode", width = 16)
    private String operationMode;
    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.operator", width = 20)
    private String operatorName;
    @ExcelExportColumn(order = 14, headerKey = "excel.settlement.reviewer", width = 20)
    private String reviewerName;
    @ExcelExportColumn(order = 15, headerKey = "excel.settlement.operationReason", width = 36)
    private String operationReason;
    @ExcelExportColumn(order = 16, headerKey = "excel.settlement.reviewComment", width = 36)
    private String reviewComment;
    @ExcelExportColumn(order = 17, headerKey = "excel.settlement.postedTime", width = 22)
    private LocalDateTime postedTime;
    @ExcelExportColumn(order = 18, headerKey = "excel.settlement.idempotencyKey", width = 36)
    private String idempotencyKey;
    @ExcelExportColumn(order = 19, headerKey = "excel.settlement.reversalOfLedgerId", width = 22)
    private Long reversalOfLedgerId;
}
