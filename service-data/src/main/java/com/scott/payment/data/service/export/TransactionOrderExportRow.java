package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成管理端交易主单 Excel 的安全行模型，与 Admin 内部导出投影一一对应。
 */
@Data
public class TransactionOrderExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.transaction.order.rootTransactionId", width = 28)
    private String rootTransactionId;
    @ExcelExportColumn(order = 2, headerKey = "excel.transaction.order.latestTransactionId", width = 28)
    private String latestTransactionId;
    @ExcelExportColumn(order = 3, headerKey = "excel.transaction.common.merchantId", width = 18)
    private String merchantId;
    @ExcelExportColumn(order = 4, headerKey = "excel.transaction.common.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    @ExcelExportColumn(order = 5, headerKey = "excel.transaction.common.merchantOrderId", width = 26)
    private String merchantOrderId;
    @ExcelExportColumn(order = 6, headerKey = "excel.transaction.common.transactionType", width = 18)
    private String transactionType;
    @ExcelExportColumn(order = 7, headerKey = "excel.transaction.common.transactionStatus", width = 18)
    private String transactionStatus;
    @ExcelExportColumn(order = 8, headerKey = "excel.transaction.order.lifecycleStatus", width = 18)
    private String lifecycleStatus;
    @ExcelExportColumn(order = 9, headerKey = "excel.transaction.order.currentAmount", width = 18)
    private BigDecimal currentAmount;
    @ExcelExportColumn(order = 10, headerKey = "excel.transaction.common.currency", width = 12)
    private String currentCurrency;
    @ExcelExportColumn(order = 11, headerKey = "excel.transaction.order.authorizedAmount", width = 18)
    private BigDecimal authorizedAmount;
    @ExcelExportColumn(order = 12, headerKey = "excel.transaction.order.capturedAmount", width = 18)
    private BigDecimal capturedAmount;
    @ExcelExportColumn(order = 13, headerKey = "excel.transaction.order.refundedAmount", width = 18)
    private BigDecimal refundedAmount;
    @ExcelExportColumn(order = 14, headerKey = "excel.transaction.common.transactionRate", width = 18)
    private BigDecimal transactionRate;
    @ExcelExportColumn(order = 15, headerKey = "excel.transaction.common.channelCode", width = 16)
    private String channelCode;
    @ExcelExportColumn(order = 16, headerKey = "excel.transaction.common.channelOrderNo", width = 26)
    private String channelOrderNo;
    @ExcelExportColumn(order = 17, headerKey = "excel.transaction.common.merchantResponseCode", width = 18)
    private String merchantResponseCode;
    @ExcelExportColumn(order = 18, headerKey = "excel.transaction.common.merchantResponseMessage", width = 30)
    private String merchantResponseMessage;
    @ExcelExportColumn(order = 19, headerKey = "excel.transaction.common.channelMatchStatus", width = 18)
    private String channelMatchStatus;
    @ExcelExportColumn(order = 20, headerKey = "excel.transaction.common.threeDs", width = 12)
    private String threeDs;
    @ExcelExportColumn(order = 21, headerKey = "excel.transaction.common.dcc", width = 12)
    private String dcc;
    @ExcelExportColumn(order = 22, headerKey = "excel.transaction.common.edc", width = 12)
    private String edc;
    @ExcelExportColumn(order = 23, headerKey = "excel.transaction.common.reconciliationStatus", width = 18)
    private String reconciliationStatus;
    @ExcelExportColumn(order = 24, headerKey = "excel.transaction.common.settlementStatus", width = 18)
    private String settlementStatus;
    @ExcelExportColumn(order = 25, headerKey = "excel.transaction.common.transactionDateTime", width = 22)
    private LocalDateTime transactionDateTime;
}
