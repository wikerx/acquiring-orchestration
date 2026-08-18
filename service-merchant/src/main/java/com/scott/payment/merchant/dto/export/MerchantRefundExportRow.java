package com.scott.payment.merchant.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRefundExportRow
 * @date : 2026-08-06 00:00
 * @description : 商户退款导出行，仅保留商户可见字段，不包含渠道错误、内部审批策略或 Admin 操作人。
 * @status : create
 */
@Data
public class MerchantRefundExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.refund.transactionId", width = 28)
    private String refundTransactionId;
    @ExcelExportColumn(order = 2, headerKey = "excel.refund.sourceTransactionId", width = 28)
    private String sourceTransactionId;
    @ExcelExportColumn(order = 3, headerKey = "excel.refund.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    @ExcelExportColumn(order = 4, headerKey = "excel.refund.transactionType", width = 16)
    private String transactionType;
    @ExcelExportColumn(order = 5, headerKey = "excel.refund.refundScope", width = 14)
    private String refundScope;
    @ExcelExportColumn(order = 6, headerKey = "excel.refund.amount", width = 18)
    private BigDecimal transactionAmount;
    @ExcelExportColumn(order = 7, headerKey = "excel.refund.currency", width = 12)
    private String transactionCurrency;
    @ExcelExportColumn(order = 8, headerKey = "excel.refund.status", width = 16)
    private String transactionStatus;
    @ExcelExportColumn(order = 9, headerKey = "excel.refund.approvalStatus", width = 18)
    private String approvalStatus;
    @ExcelExportColumn(order = 10, headerKey = "excel.refund.merchantMessage", width = 30)
    private String merchantVisibleMessage;
    @ExcelExportColumn(order = 11, headerKey = "excel.refund.paymentMethod", width = 18)
    private String paymentMethod;
    @ExcelExportColumn(order = 12, headerKey = "excel.refund.transactionTime", width = 22)
    private LocalDateTime transactionDateTime;
    @ExcelExportColumn(order = 13, headerKey = "excel.refund.completeTime", width = 22)
    private LocalDateTime completeTime;
}
