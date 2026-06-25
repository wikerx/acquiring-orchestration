package com.scott.payment.merchant.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户系统操作日志响应 DTO，用于页面展示已脱敏的审计记录。
 */
@Data
public class SysOperLogDTO {

    /**
     * 日志主键。
     */
    private Long id;

    /**
     * 请求链路追踪 ID。
     */
    private String traceId;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 操作模块名称。
     */
    private String moduleName;

    /**
     * 操作名称。
     */
    private String operationName;

    /**
     * 业务类型。
     */
    private Integer businessType;

    /**
     * 请求方式。
     */
    private String requestMethod;

    /**
     * 操作人名称。
     */
    private String operatorName;

    /**
     * 操作 URL。
     */
    private String operUrl;

    /**
     * 操作 IP。
     */
    private String operIp;

    /**
     * 执行耗时，单位毫秒。
     */
    private Long costTime;

    /**
     * 操作状态：0失败，1成功。
     */
    private Integer status;

    /**
     * 错误码。
     */
    private String errorCode;

    /**
     * 脱敏后的错误信息。
     */
    private String errorMsg;

    /**
     * 操作时间。
     */
    private LocalDateTime operatedAt;
}
