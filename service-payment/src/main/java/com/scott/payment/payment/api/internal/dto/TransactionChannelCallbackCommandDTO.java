package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackCommandDTO
 * @date : 2026-07-14 22:36
 * @email : scott_x@163.com
 * @description : 渠道回调内部落库命令，位于 service-payment 内部接口 DTO 层，承载 OpenAPI 回调入口完成安全校验后的回调原文和解析摘要。
 * @status : create
 */
@Data
public class TransactionChannelCallbackCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道编码，如 MPGS。
     */
    @NotBlank(message = "channelCode is required")
    private String channelCode;

    /**
     * 回调类型，默认使用 CHANNEL_CALLBACK。
     */
    private String callbackType;

    /**
     * 平台交易 ID；MPGS 回调优先从 order.id 解析。
     */
    private String transactionId;

    /**
     * 渠道订单号；MPGS 对应 order.id。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID；MPGS 对应 transaction.id。
     */
    private String channelTransactionId;

    /**
     * 渠道事件类型。
     */
    private String channelEventType;

    /**
     * 回调请求 URI。
     */
    private String requestUri;

    /**
     * HTTP 方法。
     */
    private String httpMethod;

    /**
     * 回调来源 IP。
     */
    private String sourceIp;

    /**
     * 请求头摘要，进入数据库前由支付服务统一转 JSON 和脱敏。
     */
    private Map<String, String> requestHeaders;

    /**
     * 回调原文，进入数据库前由支付服务统一脱敏。
     */
    private String requestBody;

    /**
     * 签名校验是否通过。
     */
    private Boolean signatureValid;

    /**
     * IP 白名单是否通过。
     */
    private Boolean ipAllowed;

    /**
     * OpenAPI 入口收到回调的时间。
     */
    private LocalDateTime receivedTime;
}
