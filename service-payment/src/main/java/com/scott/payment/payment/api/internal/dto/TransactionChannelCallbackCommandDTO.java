package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackCommandDTO
 * @date : 2026-07-14 22:36
 * @email : scott_x@163.com
 * @description : 渠道回调内部受理命令，位于 service-payment 内部接口 DTO 层，仅承载完成安全校验后的标准事件和不可逆报文摘要。
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

    /** 验签所使用的渠道凭据标识，例如渠道 MID；不得保存密钥。 */
    private String channelCredentialHint;

    /** 渠道稳定事件号，是 Inbox 数据库幂等键的必要组成部分。 */
    private String callbackEventId;

    /** 渠道协议归一化后的回调业务类型。 */
    private String callbackKind;

    private String rawChannelStatus;

    private String channelTradeStatus;

    private BigDecimal amount;

    private String currency;

    private String channelResponseCode;

    private String channelResponseMessage;

    /** 渠道协议解析出的非敏感扩展字段。 */
    private Map<String, String> extension = new LinkedHashMap<>();

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

    /** 回调原始报文的精确字节数；支付核心不接收或保存可逆原文。 */
    private Integer requestBodyLength;

    /** 回调原始报文的 SHA-256 十六进制摘要，用于审计关联和完整性排查。 */
    private String requestBodySha256;

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
