package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端安全拦截事件 Excel 的独立脱敏行模型。
 */
@Data
public class SecurityInterceptEventExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.securityIntercept.eventNo", width = 24)
    private String eventNo;
    @ExcelExportColumn(order = 2, headerKey = "excel.securityIntercept.eventTime", width = 22)
    private LocalDateTime eventTime;
    @ExcelExportColumn(order = 3, headerKey = "excel.securityIntercept.sourceLayer", width = 14)
    private String sourceLayer;
    @ExcelExportColumn(order = 4, headerKey = "excel.securityIntercept.eventType", width = 30)
    private String eventType;
    @ExcelExportColumn(order = 5, headerKey = "excel.securityIntercept.riskLevel", width = 12)
    private String riskLevel;
    @ExcelExportColumn(order = 6, headerKey = "excel.securityIntercept.action", width = 12)
    private String action;
    @ExcelExportColumn(order = 7, headerKey = "excel.securityIntercept.merchantId", width = 18)
    private String merchantId;
    @ExcelExportColumn(order = 8, headerKey = "excel.securityIntercept.clientIp", width = 20)
    private String clientIp;
    @ExcelExportColumn(order = 9, headerKey = "excel.securityIntercept.requestPath", width = 42)
    private String requestPath;
    @ExcelExportColumn(order = 10, headerKey = "excel.securityIntercept.traceId", width = 24)
    private String traceId;
    @ExcelExportColumn(order = 11, headerKey = "excel.securityIntercept.hitRuleCode", width = 26)
    private String hitRuleCode;
    @ExcelExportColumn(order = 12, headerKey = "excel.securityIntercept.reasonCode", width = 24)
    private String reasonCode;
    @ExcelExportColumn(order = 13, headerKey = "excel.securityIntercept.reasonMessage", width = 42)
    private String reasonMessage;
    @ExcelExportColumn(order = 14, headerKey = "excel.securityIntercept.processStatus", width = 14)
    private String processStatus;
    @ExcelExportColumn(order = 15, headerKey = "excel.securityIntercept.processedBy", width = 18)
    private String processedBy;
    @ExcelExportColumn(order = 16, headerKey = "excel.securityIntercept.processedTime", width = 22)
    private LocalDateTime processedTime;
}
