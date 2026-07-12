package com.scott.payment.channel.payment.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCapability
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道能力枚举，位于 payment-channel-library 枚举层，用于描述渠道可执行的交易动作，编码与 transaction_type 保持一致。
 * @status : create
 */
@Getter
public enum ChannelCapability {

    AUTHORIZATION("AUTHORIZATION"),

    CAPTURE("CAPTURE"),

    PAYMENT("PAYMENT"),

    PRE_AUTHORIZATION("PRE_AUTHORIZATION"),

    PRE_AUTH_COMPLETION("PRE_AUTH_COMPLETION"),

    INCREMENTAL_AUTHORIZATION("INCREMENTAL_AUTHORIZATION"),

    REFUND("REFUND"),

    VOID("VOID"),

    REVERSAL("REVERSAL"),

    CHARGEBACK("CHARGEBACK"),

    REPRESENTMENT("REPRESENTMENT"),

    RETRIEVAL_REQUEST("RETRIEVAL_REQUEST"),

    QUERY("QUERY");

    private final String code;

    ChannelCapability(String code) {
        this.code = code;
    }
}
