package com.scott.payment.component.web.operation.dto;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogRecord
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统操作日志采集记录
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogRecord
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Operation Log Record，位于 component-library/component-web 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class OperationLogRecord {

    /**
     * 链路追踪ID。
     */
    private String traceId;

    /**
     * 请求ID。
     */
    private String requestId;

    /**
     * 商户号，商户管理端或后台操作指定商户时使用。
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
     * 操作业务类型。
     */
    private Integer businessType;

    /**
     * 后端方法名称。
     */
    private String methodName;

    /**
     * HTTP 请求方式。
     */
    private String requestMethod;

    /**
     * 操作人类型。
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
     * 请求地址。
     */
    private String operUrl;

    /**
     * 客户端IP。
     */
    private String operIp;

    /**
     * 操作地点，当前版本通常由上游网关或前端传入。
     */
    private String operLocation;

    /**
     * 店铺号，商户管理端存在门店维度时使用。
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
     * 错误信息。
     */
    private String errorMsg;
}
