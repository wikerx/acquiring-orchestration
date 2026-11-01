package com.scott.payment.channel.payment.scottchannela.model.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ScottChannelA 代收、退款和卡授权操作的统一交易响应。
 *
 * <p>不同端点只返回与当前操作相关的字段。Fastjson2 默认忽略渠道后续新增字段；映射器按操作类型
 * 校验状态和交易身份，缺少关键字段时将同步结果视为 unknown，等待查询或回调勾兑。</p>
 **/
@Data
public final class TransactionResponsePayload {

    /**
     * 渠道 MID，可选，用于审计渠道响应归属。
     **/
    private String merchantId;

    /**
     * 平台系统交易号；代收和授权成功响应必填。
     **/
    private String merchantOrderNo;

    /**
     * 渠道交易号；代收和授权成功响应必填。
     **/
    private String transactionId;

    /**
     * 平台退款交易号；退款成功响应必填。
     **/
    private String merchantRefundNo;

    /**
     * 渠道退款号；退款成功响应必填。
     **/
    private String refundId;

    /**
     * 渠道授权号；卡授权生命周期成功响应必填。
     **/
    private String authorizationId;

    /**
     * 卡授权生命周期操作类型，可选。
     **/
    private String operationType;

    /**
     * 渠道确认的金额，可选。
     **/
    private BigDecimal amount;

    /**
     * 渠道确认的 ISO 4217 币种，可选。
     **/
    private String currency;

    /**
     * 渠道交易状态；成功 HTTP 响应应返回状态或可识别的外层处理中响应码。
     **/
    private String status;

    /**
     * 渠道业务响应码，可选。
     **/
    private String responseCode;

    /**
     * 渠道业务响应描述，可选。
     **/
    private String responseDescription;

    /**
     * 托管收银台跳转地址，可选。
     **/
    private String checkoutUrl;

    /**
     * 发卡行授权码，可选，在授权或查询返回时持久化。
     **/
    private String authorizationCode;

    /**
     * 收单参考号 ARN，可选，在请款或查询返回时持久化。
     **/
    private String acquirerReferenceNumber;

    /**
     * 渠道授权聚合状态，可选。
     **/
    private String authorizationStatus;
}
