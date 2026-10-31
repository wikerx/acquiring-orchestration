package com.scott.payment.channel.payment.scottchannela.model.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ScottChannelA 退款请求，原支付身份使用 {@code merchantOrderNo} 或 {@code transactionId} 二选一。
 **/
@Data
public final class RefundRequestPayload {

    /**
     * 原支付的平台系统交易号，与 {@code transactionId} 二选一。
     **/
    private String merchantOrderNo;

    /**
     * 原支付的渠道交易号，与 {@code merchantOrderNo} 二选一。
     **/
    private String transactionId;

    /**
     * 平台退款交易号，同时作为渠道退款幂等键，必填。
     **/
    private String merchantRefundNo;

    /**
     * 退款金额，必填，发送前固定校验为两位小数。
     **/
    private BigDecimal amount;

    /**
     * 平台接收退款异步通知的地址，可选。
     **/
    private String notifyUrl;

    /**
     * 渠道沙箱模拟结果，仅测试环境使用。
     **/
    private String simulationResult;
}
