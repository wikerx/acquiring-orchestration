package com.scott.payment.data.service.export;

import lombok.Data;
import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import java.time.LocalDateTime;

/**
 * 支付链接 Excel 行白名单，所有 ID/金额保留文本精度，不包含 URL 或客户资料。
 */
@Data
public class PaymentLinkExportRow {
    @ExcelExportColumn(order=1, headerKey="excel.paymentLink.linkId", width=28, forceText=true)
    private String linkId;
    @ExcelExportColumn(order=2, headerKey="excel.paymentLink.merchantId", width=28, forceText=true)
    private String merchantId;
    @ExcelExportColumn(order=3, headerKey="excel.paymentLink.merchantOrderNo", width=28, forceText=true)
    private String merchantOrderNo;
    @ExcelExportColumn(order=4, headerKey="excel.paymentLink.linkName", width=22, forceText=true)
    private String linkName;
    @ExcelExportColumn(order=5, headerKey="excel.paymentLink.currency", width=22, forceText=true)
    private String currency;
    @ExcelExportColumn(order=6, headerKey="excel.paymentLink.amount", width=22, forceText=true)
    private String amount;
    @ExcelExportColumn(order=7, headerKey="excel.paymentLink.status", width=22, forceText=true)
    private String status;
    @ExcelExportColumn(order=8, headerKey="excel.paymentLink.paymentStatus", width=22, forceText=true)
    private String paymentStatus;
    @ExcelExportColumn(order=9, headerKey="excel.paymentLink.expireTime", width=28, forceText=false)
    private LocalDateTime expireTime;
    @ExcelExportColumn(order=10, headerKey="excel.paymentLink.createTime", width=28, forceText=false)
    private LocalDateTime createTime;
    @ExcelExportColumn(order=11, headerKey="excel.paymentLink.paidTime", width=28, forceText=false)
    private LocalDateTime paidTime;
    @ExcelExportColumn(order=12, headerKey="excel.paymentLink.createdAppCode", width=22, forceText=true)
    private String createdAppCode;
    @ExcelExportColumn(order=13, headerKey="excel.paymentLink.createdBy", width=22, forceText=true)
    private String createdBy;
}
