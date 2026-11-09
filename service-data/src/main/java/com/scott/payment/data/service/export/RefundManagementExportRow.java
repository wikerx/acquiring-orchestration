package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data 生成管理端退款 Excel 的安全行模型，不包含渠道原文或支付敏感信息。
 */
@Data
public class RefundManagementExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.refund.transactionId", width = 28)
    private String refundTransactionId;
    @ExcelExportColumn(order = 2, headerKey = "excel.refund.sourceTransactionId", width = 28)
    private String sourceTransactionId;
    @ExcelExportColumn(order = 3, headerKey = "excel.refund.merchantId", width = 18)
    private String merchantId;
    @ExcelExportColumn(order = 4, headerKey = "excel.refund.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    @ExcelExportColumn(order = 5, headerKey = "excel.refund.transactionType", width = 16)
    private String transactionType;
    @ExcelExportColumn(order = 6, headerKey = "excel.refund.refundScope", width = 14)
    private String refundScope;
    @ExcelExportColumn(order = 7, headerKey = "excel.refund.requestSource", width = 18)
    private String requestSource;
    @ExcelExportColumn(order = 8, headerKey = "excel.refund.amount", width = 18)
    private BigDecimal transactionAmount;
    @ExcelExportColumn(order = 9, headerKey = "excel.refund.currency", width = 12)
    private String transactionCurrency;
    @ExcelExportColumn(order = 10, headerKey = "excel.refund.status", width = 16)
    private String transactionStatus;
    @ExcelExportColumn(order = 11, headerKey = "excel.refund.approvalStatus", width = 18)
    private String approvalStatus;
    @ExcelExportColumn(order = 12, headerKey = "excel.refund.applicant", width = 22)
    private String applicantName;
    @ExcelExportColumn(order = 13, headerKey = "excel.refund.channelCode", width = 16)
    private String channelCode;
    @ExcelExportColumn(order = 14, headerKey = "excel.refund.channelOrderNo", width = 26)
    private String channelOrderNo;
    @ExcelExportColumn(order = 15, headerKey = "excel.refund.transactionTime", width = 22)
    private LocalDateTime transactionDateTime;
    @ExcelExportColumn(order = 16, headerKey = "excel.refund.completeTime", width = 22)
    private LocalDateTime completeTime;
}
