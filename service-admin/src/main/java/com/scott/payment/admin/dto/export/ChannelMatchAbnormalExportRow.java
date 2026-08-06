package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalExportRow
 * @date : 2026-08-06 00:00
 * @description : Admin 勾兑异常导出行，只包含脱敏案件证据和处置状态，不导出渠道原始报文。
 * @status : create
 */
@Data
public class ChannelMatchAbnormalExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.abnormal.eventId", width = 28)
    private String abnormalEventId;
    @ExcelExportColumn(order = 2, headerKey = "excel.abnormal.transactionId", width = 28)
    private String transactionId;
    @ExcelExportColumn(order = 3, headerKey = "excel.abnormal.merchantId", width = 18)
    private String merchantId;
    @ExcelExportColumn(order = 4, headerKey = "excel.abnormal.merchantOrderNo", width = 26)
    private String merchantOrderNo;
    @ExcelExportColumn(order = 5, headerKey = "excel.abnormal.type", width = 24)
    private String abnormalType;
    @ExcelExportColumn(order = 6, headerKey = "excel.abnormal.level", width = 14)
    private String abnormalLevel;
    @ExcelExportColumn(order = 7, headerKey = "excel.abnormal.status", width = 16)
    private String eventStatus;
    @ExcelExportColumn(order = 8, headerKey = "excel.abnormal.platformStatus", width = 18)
    private String platformStatus;
    @ExcelExportColumn(order = 9, headerKey = "excel.abnormal.channelCode", width = 16)
    private String channelCode;
    @ExcelExportColumn(order = 10, headerKey = "excel.abnormal.channelStatus", width = 18)
    private String channelStatus;
    @ExcelExportColumn(order = 11, headerKey = "excel.abnormal.amount", width = 18)
    private BigDecimal platformAmount;
    @ExcelExportColumn(order = 12, headerKey = "excel.abnormal.currency", width = 12)
    private String platformCurrency;
    @ExcelExportColumn(order = 13, headerKey = "excel.abnormal.occurrences", width = 14)
    private Integer occurrenceCount;
    @ExcelExportColumn(order = 14, headerKey = "excel.abnormal.assignee", width = 22)
    private String assignedToName;
    @ExcelExportColumn(order = 15, headerKey = "excel.abnormal.firstSeenTime", width = 22)
    private LocalDateTime firstSeenTime;
    @ExcelExportColumn(order = 16, headerKey = "excel.abnormal.lastSeenTime", width = 22)
    private LocalDateTime lastSeenTime;
}
