package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptEventExportRow
 * @date : 2026-07-18
 * @email : scott_x@163.com
 * @description : 安全拦截事件导出行对象，位于 service-admin 导出传输层，只导出已脱敏的事件排查字段。
 * @status : create
 */
@Data
public class SecurityInterceptEventExportRow {

    /**
     * 事件号。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.securityIntercept.eventNo", width = 24)
    private String eventNo;

    /**
     * 事件时间。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.securityIntercept.eventTime", width = 22)
    private LocalDateTime eventTime;

    /**
     * 来源层级。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.securityIntercept.sourceLayer", width = 14)
    private String sourceLayer;

    /**
     * 事件类型。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.securityIntercept.eventType", width = 30)
    private String eventType;

    /**
     * 风险等级。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.securityIntercept.riskLevel", width = 12)
    private String riskLevel;

    /**
     * 处置动作。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.securityIntercept.action", width = 12)
    private String action;

    /**
     * 商户号。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.securityIntercept.merchantId", width = 18)
    private String merchantId;

    /**
     * 客户端 IP。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.securityIntercept.clientIp", width = 20)
    private String clientIp;

    /**
     * 请求路径。
     */
    @ExcelExportColumn(order = 9, headerKey = "excel.securityIntercept.requestPath", width = 42)
    private String requestPath;

    /**
     * traceId。
     */
    @ExcelExportColumn(order = 10, headerKey = "excel.securityIntercept.traceId", width = 24)
    private String traceId;

    /**
     * 命中规则编码。
     */
    @ExcelExportColumn(order = 11, headerKey = "excel.securityIntercept.hitRuleCode", width = 26)
    private String hitRuleCode;

    /**
     * 原因码。
     */
    @ExcelExportColumn(order = 12, headerKey = "excel.securityIntercept.reasonCode", width = 24)
    private String reasonCode;

    /**
     * 原因说明。
     */
    @ExcelExportColumn(order = 13, headerKey = "excel.securityIntercept.reasonMessage", width = 42)
    private String reasonMessage;

    /**
     * 处理状态。
     */
    @ExcelExportColumn(order = 14, headerKey = "excel.securityIntercept.processStatus", width = 14)
    private String processStatus;

    /**
     * 处理人。
     */
    @ExcelExportColumn(order = 15, headerKey = "excel.securityIntercept.processedBy", width = 18)
    private String processedBy;

    /**
     * 处理时间。
     */
    @ExcelExportColumn(order = 16, headerKey = "excel.securityIntercept.processedTime", width = 22)
    private LocalDateTime processedTime;
}
