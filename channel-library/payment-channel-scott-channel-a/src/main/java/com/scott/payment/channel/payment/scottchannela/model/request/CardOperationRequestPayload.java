package com.scott.payment.channel.payment.scottchannela.model.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * ScottChannelA 请款、预授权完成、增量授权或取消请求。
 **/
@Data
public final class CardOperationRequestPayload {

    /**
     * 原授权返回的渠道授权号，必填。
     **/
    private String authorizationId;

    /**
     * 当前平台操作交易号，同时作为渠道侧幂等键，必填。
     **/
    private String merchantOrderNo;

    /**
     * 操作金额；取消操作不发送，其余操作必填。
     **/
    private BigDecimal amount;

    /**
     * 平台接收操作异步通知的地址，可选。
     **/
    private String notifyUrl;

    /**
     * 渠道沙箱模拟结果，仅测试环境使用。
     **/
    private String simulationResult;
}
