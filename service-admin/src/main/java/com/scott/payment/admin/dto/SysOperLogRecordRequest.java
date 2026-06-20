package com.scott.payment.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysOperLogRecordRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统后台操作日志写入请求
 * @status : create
 */
@Data
public class SysOperLogRecordRequest {

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
     * 商户号，后台操作涉及商户时记录。
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
     * 后端方法名称。
     */
    private String methodName;

    /**
     * 请求方式。
     */
    private String requestMethod;

    /**
     * 操作人类别。
     */
    private Integer operatorType;

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
     * 操作地点。
     */
    private String operLocation;

    /**
     * 店铺号。
     */
    private String storeId;

    /**
     * 浏览器 User-Agent。
     */
    private String userAgent;

    /**
     * 脱敏后的请求参数。
     */
    private String requestParam;

    /**
     * 脱敏后的响应结果。
     */
    private String responseResult;

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
     * 原始操作时间，优先使用消息生产时刻，避免异步消费后把审计时间覆盖为落库时间。
     */
    private LocalDateTime operatedAt;
}
