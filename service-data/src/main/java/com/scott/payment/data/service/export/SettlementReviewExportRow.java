package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data 生成管理端结算审核单 Excel 的独立行模型，保留 Maker-Checker 审计结果。
 */
@Data
public class SettlementReviewExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.reviewOrderNo", width = 24)
    private String reviewOrderNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.reviewType", width = 18)
    private String reviewType;
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.createMode", width = 16)
    private String createMode;
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.merchantId", width = 20)
    private String merchantId;
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.targetCurrency", width = 14)
    private String targetCurrency;
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.candidateCount", width = 14)
    private Integer candidateCount;
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.netDirection", width = 14)
    private String netDirection;
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.netAmount", width = 20)
    private BigDecimal netAmount;
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.reviewStatus", width = 18)
    private String reviewStatus;
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.submitter", width = 20)
    private String submittedByAccountName;
    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.submittedTime", width = 22)
    private LocalDateTime submittedTime;
    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.reviewer", width = 20)
    private String decidedByAccountName;
    @ExcelExportColumn(order = 14, headerKey = "excel.settlement.decisionAction", width = 16)
    private String decisionAction;
    @ExcelExportColumn(order = 15, headerKey = "excel.settlement.decisionTime", width = 22)
    private LocalDateTime decisionTime;
    @ExcelExportColumn(order = 16, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
}
