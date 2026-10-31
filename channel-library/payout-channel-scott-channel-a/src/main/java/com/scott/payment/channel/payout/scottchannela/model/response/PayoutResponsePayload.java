package com.scott.payment.channel.payout.scottchannela.model.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ScottChannelA 代付提交、查询和取消的交易响应。
 *
 * <p>Fastjson2 默认忽略渠道后续新增字段。成功 HTTP 响应缺少状态或交易身份时，调用结果按 unknown
 * 处理，避免把未确认的资金动作错误标记为失败。</p>
 **/
@Data
public final class PayoutResponsePayload {

    /**
     * 渠道 MID，可选，用于审计渠道响应归属。
     **/
    private String merchantId;

    /**
     * 平台代付单号，成功响应必填。
     **/
    private String merchantOrderNo;

    /**
     * 渠道代付交易号，成功响应必填。
     **/
    private String transactionId;

    /**
     * 渠道代付状态，成功响应必填。
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
     * 渠道确认的代付方式，可选。
     **/
    private String paymentMethod;

    /**
     * 渠道确认的代付金额，可选。
     **/
    private BigDecimal amount;

    /**
     * 渠道确认的 ISO 4217 币种，可选。
     **/
    private String currency;
}
