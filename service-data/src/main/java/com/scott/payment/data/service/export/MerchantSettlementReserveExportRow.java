package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data 生成商户保证金结算 Excel 的安全行模型，金额保持原标签币种责任语义。
 */
@Data
public class MerchantSettlementReserveExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.reserveNo", width = 26)
    private String reserveNo;
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.transactionId", width = 28, forceText = true)
    private String sourceTransactionId;
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.transactionTime", width = 22)
    private LocalDateTime sourceTransactionDateTime;
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.reserveAction", width = 20)
    private String actionType;
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.direction", width = 12)
    private String direction;
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.sourceCurrency", width = 14)
    private String currency;
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.sourceAmount", width = 20)
    private BigDecimal amount;
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.reserveRemaining", width = 20)
    private BigDecimal remainingAmount;
    @ExcelExportColumn(order = 11, headerKey = "excel.settlement.reserveStatus", width = 18)
    private String reserveStatus;
    @ExcelExportColumn(order = 12, headerKey = "excel.settlement.expectedReleaseDate", width = 18)
    private LocalDate expectedReleaseDate;
    @ExcelExportColumn(order = 13, headerKey = "excel.settlement.reserveActionTime", width = 22)
    private LocalDateTime actionTime;
}
