package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogDTO
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统后台操作日志响应 DTO
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Oper Log 数据传输对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysOperLogDTO {

    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 链路追踪ID。
     */
    private String traceId;

    /**
     * 请求ID。
     */
    private String requestId;

    /**
     * MQ 消息唯一标识。
     */
    private String messageId;

    /**
     * 消费幂等键。
     */
    private String idempotentKey;

    /**
     * 系统编码。
     */
    private String systemCode;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 模块名称。
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
     * 操作人ID。
     */
    private String operatorId;

    /**
     * 操作人名称。
     */
    private String operatorName;

    /**
     * 请求URL。
     */
    private String operUrl;

    /**
     * 操作IP。
     */
    private String operIp;

    /**
     * 店铺号。
     */
    private String storeId;

    /**
     * 浏览器 User-Agent。
     */
    private String userAgent;

    /**
     * 执行时长，单位毫秒。
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
     * 错误信息。
     */
    private String errorMsg;

    /**
     * 操作时间。
     */
    private LocalDateTime operatedAt;
}
