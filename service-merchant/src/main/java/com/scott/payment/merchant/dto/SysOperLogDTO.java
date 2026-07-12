package com.scott.payment.merchant.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Sys Oper Log 数据传输对象，位于 service-merchant 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
