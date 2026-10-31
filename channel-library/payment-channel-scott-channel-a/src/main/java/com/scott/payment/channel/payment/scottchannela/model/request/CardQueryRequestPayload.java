package com.scott.payment.channel.payment.scottchannela.model.request;

import lombok.Data;

/**
 * ScottChannelA 卡授权生命周期查询请求，三个查询标识必须且只能填写一个。
 **/
@Data
public final class CardQueryRequestPayload {

    /**
     * 平台系统交易号，可作为唯一查询条件。
     **/
    private String merchantOrderNo;

    /**
     * 渠道交易号，可作为唯一查询条件。
     **/
    private String transactionId;

    /**
     * 渠道授权号，可作为唯一查询条件。
     **/
    private String authorizationId;
}
