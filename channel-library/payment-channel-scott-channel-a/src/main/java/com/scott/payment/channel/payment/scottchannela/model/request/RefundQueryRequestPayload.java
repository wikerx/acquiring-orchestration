package com.scott.payment.channel.payment.scottchannela.model.request;

import lombok.Data;

/**
 * ScottChannelA 退款查询请求，两个退款标识必须且只能填写一个。
 **/
@Data
public final class RefundQueryRequestPayload {

    /**
     * 平台退款交易号，与 {@code refundId} 二选一。
     **/
    private String merchantRefundNo;

    /**
     * 渠道退款号，与 {@code merchantRefundNo} 二选一。
     **/
    private String refundId;
}
