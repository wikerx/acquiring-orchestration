package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成商户交易 Excel 的安全行模型，金额和币种固定使用商户标签口径。
 */
@Data
public class MerchantTransactionExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.transaction.operation.transactionId", width = 28)
    private String transactionId;
    @ExcelExportColumn(order = 2, headerKey = "excel.transaction.operation.sourceTransactionId", width = 28)
    private String sourceTransactionId;
    @ExcelExportColumn(order = 3, headerKey = "excel.transaction.common.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    @ExcelExportColumn(order = 4, headerKey = "excel.transaction.common.merchantOrderId", width = 26)
    private String merchantOrderId;
    @ExcelExportColumn(order = 5, headerKey = "excel.transaction.common.transactionType", width = 18)
    private String transactionType;
    @ExcelExportColumn(order = 6, headerKey = "excel.transaction.common.transactionStatus", width = 18)
    private String transactionStatus;
    @ExcelExportColumn(order = 7, headerKey = "excel.transaction.operation.transactionAmount", width = 18)
    private BigDecimal transactionAmount;
    @ExcelExportColumn(order = 8, headerKey = "excel.transaction.common.currency", width = 12)
    private String transactionCurrency;
    @ExcelExportColumn(order = 9, headerKey = "excel.transaction.common.merchantResponseCode", width = 18)
    private String merchantResponseCode;
    @ExcelExportColumn(order = 10, headerKey = "excel.transaction.common.merchantResponseMessage", width = 30)
    private String merchantResponseMessage;
    @ExcelExportColumn(order = 11, headerKey = "excel.transaction.operation.merchantNotificationStatus", width = 20)
    private String merchantNotificationStatus;
    @ExcelExportColumn(order = 12, headerKey = "excel.transaction.operation.paymentMethod", width = 18)
    private String paymentMethod;
    @ExcelExportColumn(order = 13, headerKey = "excel.transaction.operation.paymentBrand", width = 18)
    private String paymentBrand;
    @ExcelExportColumn(order = 14, headerKey = "excel.transaction.operation.cardBin", width = 16)
    private String cardBin;
    @ExcelExportColumn(order = 15, headerKey = "excel.transaction.operation.cardNumberMasked", width = 22)
    private String cardNumberMasked;
    @ExcelExportColumn(order = 16, headerKey = "excel.transaction.common.threeDs", width = 12)
    private String threeDs;
    @ExcelExportColumn(order = 17, headerKey = "excel.transaction.operation.authCode", width = 16)
    private String authCode;
    @ExcelExportColumn(order = 18, headerKey = "excel.transaction.common.settlementStatus", width = 18)
    private String settlementStatus;
    @ExcelExportColumn(order = 19, headerKey = "excel.transaction.common.transactionDateTime", width = 22)
    private LocalDateTime transactionDateTime;
    @ExcelExportColumn(order = 20, headerKey = "excel.transaction.operation.operationTime", width = 22)
    private LocalDateTime operationTime;
}
