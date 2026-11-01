package com.scott.payment.channel.payment.enums;

import lombok.Getter;

/**
 * 渠道capability枚举，位于 渠道适配库，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 */
@Getter
public enum ChannelCapability {

    /**
     * 3DS AUTHENTICATION 枚举值，表示渠道提供统一 3DS 后端阶段 API；不代表业务层已实现浏览器交互和端到端支付门禁。
     */
    THREE_DS_AUTHENTICATION("THREE_DS_AUTHENTICATION"),

    /**
     * AUTHORIZATION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    AUTHORIZATION("AUTHORIZATION"),

    /**
     * CAPTURE 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CAPTURE("CAPTURE"),

    /**
     * PAYMENT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PAYMENT("PAYMENT"),

    /**
     * PRE AUTHORIZATION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PRE_AUTHORIZATION("PRE_AUTHORIZATION"),

    /**
     * PRE AUTH COMPLETION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PRE_AUTH_COMPLETION("PRE_AUTH_COMPLETION"),

    /**
     * INCREMENTAL AUTHORIZATION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    INCREMENTAL_AUTHORIZATION("INCREMENTAL_AUTHORIZATION"),

    /**
     * REFUND 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REFUND("REFUND"),

    /**
     * VOID 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    VOID("VOID"),

    /**
     * REVERSAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REVERSAL("REVERSAL"),

    /**
     * CHARGEBACK 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CHARGEBACK("CHARGEBACK"),

    /**
     * REPRESENTMENT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REPRESENTMENT("REPRESENTMENT"),

    /**
     * RETRIEVAL REQUEST 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RETRIEVAL_REQUEST("RETRIEVAL_REQUEST"),

    /**
     * QUERY 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    QUERY("QUERY");


    private final String code;

    /**
     * 绑定渠道 SPI 能力与稳定协议编码。
     *
     * @param code 能力编码
     */
    ChannelCapability(String code) {
        this.code = code;
    }
}
