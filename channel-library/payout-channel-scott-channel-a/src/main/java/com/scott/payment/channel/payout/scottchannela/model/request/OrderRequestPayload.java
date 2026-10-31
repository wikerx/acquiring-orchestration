package com.scott.payment.channel.payout.scottchannela.model.request;

import lombok.Data;

/**
 * ScottChannelA 代付查询或取消请求，两个渠道身份必须且只能填写一个。
 **/
@Data
public final class OrderRequestPayload {

    /**
     * 平台代付单号，与 {@code transactionId} 二选一。
     **/
    private String merchantOrderNo;

    /**
     * 渠道代付交易号，与 {@code merchantOrderNo} 二选一。
     **/
    private String transactionId;
}
