package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * service-openapi 向 service-payment Inbox 提交已验证标准事件的内部请求 DTO；只携带报文长度和不可逆摘要，不跨服务传递回调原文。
 */
@Data
public class TransactionChannelCallbackClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;

    /**
     * 回调事件类型，用于区分渠道授权、请款、退款、撤销和状态同步事件。
     */
    private String callbackType;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;

    /**
     * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
     */
    private String channelOrderNo;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String channelTransactionId;

    /**
     * 回调事件类型，用于区分渠道授权、请款、退款、撤销和状态同步事件。
     */
    private String channelEventType;

    /**
     * 验签所使用的渠道凭据标识，例如渠道 MID；不得保存密钥。
     */
    private String channelCredentialHint;

    /**
     * 渠道稳定事件号，是 Inbox 数据库幂等键的必要组成部分。
     */
    private String callbackEventId;

    /**
     * 渠道协议归一化后的回调业务类型。
     */
    private String callbackKind;

    /**
     * Provider raw status captured before platform state resolution.
     */
    private String rawChannelStatus;

    /**
     * Provider-normalized channel trade status.
     */
    private String channelTradeStatus;


    private BigDecimal amount;


    private String currency;


    private String channelResponseCode;


    private String channelResponseMessage;

    /**
     * 渠道协议解析出的非敏感扩展字段。
     */
    private Map<String, String> extension = new LinkedHashMap<>();


    private String requestUri;


    private String httpMethod;


    private String sourceIp;


    private Map<String, String> requestHeaders;

    /**
     * 回调原始报文的精确字节数；支付核心不接收或保存可逆原文。
     */
    private Integer requestBodyLength;

    /**
     * 回调原始报文的 SHA-256 十六进制摘要。
     */
    private String requestBodySha256;


    private Boolean signatureValid;


    private Boolean ipAllowed;


    private LocalDateTime receivedTime;
}
