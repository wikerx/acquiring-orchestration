package com.scott.payment.channel.payment.scottchannela.model.request;

import lombok.Data;

/**
 * ScottChannelA 代收查询请求，两个查询标识必须且只能填写一个。
 **/
@Data
public final class OrderQueryRequestPayload {

    /**
     * 平台系统交易号，与 {@code transactionId} 二选一。
     **/
    private String merchantOrderNo;

    /**
     * 渠道交易号，与 {@code merchantOrderNo} 二选一。
     **/
    private String transactionId;
}
