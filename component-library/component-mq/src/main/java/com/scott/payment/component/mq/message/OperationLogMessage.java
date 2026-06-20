package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogMessage
 * @date : 2026-06-20 01:29
 * @email : scott_x@163.com
 * @description : 管理类系统操作日志 MQ 消息体
 * @status : create
 *
 * <p>该消息体仅承载已经脱敏和截断后的审计字段，禁止放入明文卡号、CVV、JWT、
 * 私钥、API Key、完整异常堆栈等敏感信息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogMessage extends BaseMqMessage {

    /**
     * 消费幂等键。
     */
    private String idempotentKey;

    /**
     * 系统编码，区分 ADMIN / MERCHANT。
     */
    private String systemCode;

    /**
     * 请求ID。
     */
    private String requestId;

    /**
     * 链路追踪ID。
     */
    private String traceId;

    /**
     * 操作模块名称。
     */
    private String operationModule;

    /**
     * 操作名称。
     */
    private String operationName;

    /**
     * 操作类型编码。
     */
    private String operationType;

    /**
     * 后端方法名称。
     */
    private String methodName;

    /**
     * HTTP 请求方式。
     */
    private String requestMethod;

    /**
     * 请求 URI。
     */
    private String requestUri;

    /**
     * 操作人 ID。
     */
    private String operatorId;

    /**
     * 操作人名称。
     */
    private String operatorName;

    /**
     * 操作人类型。
     */
    private String operatorType;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 店铺号。
     */
    private String storeId;

    /**
     * 客户端 IP。
     */
    private String clientIp;

    /**
     * 浏览器 User-Agent。
     */
    private String userAgent;

    /**
     * 脱敏后的请求参数。
     */
    private String requestParams;

    /**
     * 脱敏后的响应结果。
     */
    private String responseResult;

    /**
     * 错误摘要。
     */
    private String errorMessage;

    /**
     * 操作状态：0失败，1成功。
     */
    private Integer operationStatus;

    /**
     * 执行耗时，单位毫秒。
     */
    private Long costTimeMs;

    /**
     * 实际操作时间。
     */
    private LocalDateTime operationTime;

    /**
     * 错误码。
     */
    private String errorCode;
}
