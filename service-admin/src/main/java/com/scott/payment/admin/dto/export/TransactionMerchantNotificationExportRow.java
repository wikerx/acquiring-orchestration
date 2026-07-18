package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationExportRow
 * @date : 2026-07-17
 * @email : scott_x@163.com
 * @description : 商户通知任务导出行对象，位于 service-admin 导出传输层，仅导出通知排查所需的安全字段。
 * @status : create
 */
@Data
public class TransactionMerchantNotificationExportRow {

    /**
     * 商户通知任务号。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.transaction.notification.notifyId", width = 28)
    private String notifyId;

    /**
     * 平台交易号。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.transaction.operation.transactionId", width = 28)
    private String transactionId;

    /**
     * 交易动作 ID。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.transaction.notification.operationId", width = 26)
    private String operationId;

    /**
     * 平台商户号。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.transaction.common.merchantId", width = 18)
    private String merchantId;

    /**
     * 商户订单号。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.transaction.common.merchantOrderNo", width = 26)
    private String merchantOrderNo;

    /**
     * 通知类型。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.transaction.notification.notifyType", width = 16)
    private String notifyType;

    /**
     * 事件类型。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.transaction.notification.eventType", width = 18)
    private String eventType;

    /**
     * 通知状态。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.transaction.notification.notifyStatus", width = 18)
    private String notifyStatus;

    /**
     * 最近一次尝试次数。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.transaction.notification.lastAttemptNo", width = 16)
    private Integer lastAttemptNo;

    /**
     * 最大重试次数。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.transaction.notification.maxRetryCount", width = 16)
    private Integer maxRetryCount;

    /**
     * 下次重试时间。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.transaction.notification.nextRetryTime", width = 22)
    private LocalDateTime nextRetryTime;

    /**
     * 最近通知时间。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.transaction.notification.lastNotifyTime", width = 22)
    private LocalDateTime lastNotifyTime;

    /**
     * 最近失败原因。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.transaction.notification.lastFailReason", width = 36)
    private String lastFailReason;

    /**
     * 交易发生时间。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.transaction.common.transactionDateTime", width = 22)
    private LocalDateTime transactionDateTime;

    /**
     * 创建时间。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.transaction.common.createTime", width = 22)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.transaction.common.updateTime", width = 22)
    private LocalDateTime updateTime;
}
