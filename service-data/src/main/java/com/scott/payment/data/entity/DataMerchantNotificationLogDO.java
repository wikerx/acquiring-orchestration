package com.scott.payment.data.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationLogDO
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知尝试日志实体，只保存脱敏且长度受控的请求响应摘要和执行结果
 * @status : create
 */
@Data
public class DataMerchantNotificationLogDO {

    /** 通知尝试日志业务 ID，不允许为空。 */
    private String notifyLogId;

    /** 稳定通知 ID，不允许为空。 */
    private String notifyId;

    /** 本次回调协议事件号；人工回调必须使用 MQ 消息号且不允许为空。 */
    private String callbackEventId;

    /** 投递模式：AUTO 表示系统计划，MANUAL 表示管理后台人工命令。 */
    private String deliveryMode;

    /** 平台交易 ID，不允许为空。 */
    private String transactionId;

    /** 平台交易生命周期操作 ID，允许为空。 */
    private String operationId;

    /** 平台商户号，不允许为空。 */
    private String merchantId;

    /** 本次尝试序号，从 1 开始。 */
    private Integer attemptNo;

    /** 回调 URL 的 SHA-256 摘要，不包含实际 URL。 */
    private String targetUrlHash;

    /** HTTP 状态码；建连失败或超时时允许为空。 */
    private Integer httpStatus;

    /** 脱敏后的请求头 JSON，不允许包含认证令牌或签名原文。 */
    private String requestHeaderJsonMasked;

    /** 脱敏后的请求体 JSON。 */
    private String requestBodyJsonMasked;

    /** 脱敏且长度受控的响应体 JSON。 */
    private String responseBodyJsonMasked;

    /** 成功标记，1 表示 HTTP 2xx，0 表示失败。 */
    private Integer success;

    /** 长度受控的失败原因，不包含内部密钥或完整响应。 */
    private String errorMessage;

    /** HTTP 尝试开始时间，精度为毫秒。 */
    private LocalDateTime notifyTime;

    /** HTTP 尝试耗时，单位毫秒。 */
    private Integer durationMillis;

    /** 交易业务时间，用于定位物理分表。 */
    private LocalDateTime transactionDateTime;

    /** 交易 UTC 时间，精度为毫秒。 */
    private LocalDateTime transactionUtcTime;

    /** 交易时间所属 IANA 时区。 */
    private String transactionTimeZone;

    /** 日志创建时间，精度为毫秒。 */
    private LocalDateTime createTime;
}
