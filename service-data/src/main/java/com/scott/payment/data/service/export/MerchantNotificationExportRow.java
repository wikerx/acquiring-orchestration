package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端商户通知记录 Excel 的安全行模型，不包含地址、载荷和响应正文。
 */
@Data
public class MerchantNotificationExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.transaction.notification.notifyId", width = 28)
    private String notifyId;
    @ExcelExportColumn(order = 2, headerKey = "excel.transaction.operation.transactionId", width = 28)
    private String transactionId;
    @ExcelExportColumn(order = 3, headerKey = "excel.transaction.notification.operationId", width = 26)
    private String operationId;
    @ExcelExportColumn(order = 4, headerKey = "excel.transaction.common.merchantId", width = 18)
    private String merchantId;
    @ExcelExportColumn(order = 5, headerKey = "excel.transaction.common.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    @ExcelExportColumn(order = 6, headerKey = "excel.transaction.notification.notifyType", width = 16)
    private String notifyType;
    @ExcelExportColumn(order = 7, headerKey = "excel.transaction.notification.eventType", width = 18)
    private String eventType;
    @ExcelExportColumn(order = 8, headerKey = "excel.transaction.notification.notifyStatus", width = 18)
    private String notifyStatus;
    @ExcelExportColumn(order = 9, headerKey = "excel.transaction.notification.lastAttemptNo", width = 16)
    private Integer lastAttemptNo;
    @ExcelExportColumn(order = 10, headerKey = "excel.transaction.notification.maxRetryCount", width = 16)
    private Integer maxRetryCount;
    @ExcelExportColumn(order = 11, headerKey = "excel.transaction.notification.nextRetryTime", width = 22)
    private LocalDateTime nextRetryTime;
    @ExcelExportColumn(order = 12, headerKey = "excel.transaction.notification.lastNotifyTime", width = 22)
    private LocalDateTime lastNotifyTime;
    @ExcelExportColumn(order = 13, headerKey = "excel.transaction.notification.lastFailReason", width = 36)
    private String lastFailReason;
    @ExcelExportColumn(order = 14, headerKey = "excel.transaction.common.transactionDateTime", width = 22)
    private LocalDateTime transactionDateTime;
    @ExcelExportColumn(order = 15, headerKey = "excel.transaction.common.createTime", width = 22)
    private LocalDateTime createTime;
    @ExcelExportColumn(order = 16, headerKey = "excel.transaction.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
