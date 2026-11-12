package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data 生成商户结算批次 Excel 的安全行模型，只包含商户可见净入账结果。
 */
@Data
public class MerchantSettlementBatchExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.settlement.batchNo", width = 24)
    private String settlementBatchNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.settlement.businessDate", width = 16)
    private LocalDate businessDate;
    @ExcelExportColumn(order = 3, headerKey = "excel.settlement.batchType", width = 18)
    private String batchType;
    @ExcelExportColumn(order = 4, headerKey = "excel.settlement.status", width = 16)
    private String batchStatus;
    @ExcelExportColumn(order = 5, headerKey = "excel.settlement.targetCurrency", width = 14)
    private String targetCurrency;
    @ExcelExportColumn(order = 6, headerKey = "excel.settlement.transactionCount", width = 14)
    private Long transactionCount;
    @ExcelExportColumn(order = 7, headerKey = "excel.settlement.settlementItemCount", width = 16)
    private Integer settlementItemCount;
    @ExcelExportColumn(order = 8, headerKey = "excel.settlement.direction", width = 12)
    private String netDirection;
    @ExcelExportColumn(order = 9, headerKey = "excel.settlement.targetAmount", width = 20)
    private BigDecimal netAmount;
    @ExcelExportColumn(order = 10, headerKey = "excel.settlement.postedTime", width = 22)
    private LocalDateTime postedTime;
}
