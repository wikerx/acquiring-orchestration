package com.scott.payment.channel.payment.scottchannela.model.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ScottChannelA 普通授权或预授权创建请求。
 **/
@Data
public final class CardAuthorizationRequestPayload {

    /**
     * 平台系统交易号，同时作为渠道侧商户订单号和幂等键，必填。
     **/
    private String merchantOrderNo;

    /**
     * 授权金额，必填，发送前固定校验为两位小数。
     **/
    private BigDecimal amount;

    /**
     * ISO 4217 大写币种代码，必填。
     **/
    private String currency;

    /**
     * 平台接收授权异步通知的地址，可选。
     **/
    private String notifyUrl;

    /**
     * 付款方 IP，可选。
     **/
    private String clientIp;

    /**
     * 平台请求时间，ISO 本地日期时间格式，必填。
     **/
    private String requestTime;

    /**
     * 渠道沙箱模拟结果，仅测试环境使用。
     **/
    private String simulationResult;
}
