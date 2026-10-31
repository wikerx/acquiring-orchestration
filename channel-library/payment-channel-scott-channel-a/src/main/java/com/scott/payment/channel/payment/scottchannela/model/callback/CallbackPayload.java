package com.scott.payment.channel.payment.scottchannela.model.callback;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ScottChannelA 异步回调正文。
 *
 * <p>Fastjson2 默认忽略未声明字段，以兼容渠道扩展回调内容。验签必须基于原始请求字节完成，
 * 解析后的对象仅用于身份、状态和交易证据校验。</p>
 **/
@Data
public final class CallbackPayload {

    /**
     * 渠道回调事件号，必填并与请求头一致。
     **/
    private String eventId;

    /**
     * 回调事件类型，用于区分支付、退款和卡授权事件。
     **/
    private String eventType;

    /**
     * 渠道 MID，必填并与请求头一致。
     **/
    private String merchantId;

    /**
     * 平台系统交易号；非授权回调的渠道订单身份。
     **/
    private String merchantOrderNo;

    /**
     * 渠道交易号；退款回调可由 {@code refundId} 取代。
     **/
    private String transactionId;

    /**
     * 平台退款交易号，可选。
     **/
    private String merchantRefundNo;

    /**
     * 渠道退款号，退款回调应返回。
     **/
    private String refundId;

    /**
     * 渠道授权号；授权回调的渠道订单身份。
     **/
    private String authorizationId;

    /**
     * 卡授权生命周期操作类型，可选。
     **/
    private String operationType;

    /**
     * 渠道交易状态，必填。
     **/
    private String status;

    /**
     * 渠道确认金额，可选。
     **/
    private BigDecimal amount;

    /**
     * 渠道确认币种，可选。
     **/
    private String currency;

    /**
     * 渠道业务类型，可选，写入回调扩展证据。
     **/
    private String businessType;

    /**
     * 渠道支付场景，可选，写入回调扩展证据。
     **/
    private String paymentScene;

    /**
     * 渠道支付方式，可选，写入回调扩展证据。
     **/
    private String paymentMethod;

    /**
     * 渠道业务响应码，可选。
     **/
    private String responseCode;

    /**
     * 渠道业务响应描述，可选。
     **/
    private String responseDescription;

    /**
     * 兼容部分回调使用的外层响应码字段。
     **/
    private String code;

    /**
     * 兼容部分回调使用的外层响应描述字段。
     **/
    private String message;

    /**
     * 渠道授权聚合状态，可选。
     **/
    private String authorizationStatus;

    /**
     * 发卡行授权码，可选，写入回调扩展证据。
     **/
    private String authorizationCode;

    /**
     * 收单参考号 ARN，可选，写入回调扩展证据。
     **/
    private String acquirerReferenceNumber;

    /**
     * 累计授权金额，可选。
     **/
    private BigDecimal authorizedAmount;

    /**
     * 累计已请款金额，可选。
     **/
    private BigDecimal capturedAmount;

    /**
     * 当前剩余可操作金额，可选。
     **/
    private BigDecimal availableAmount;
}
