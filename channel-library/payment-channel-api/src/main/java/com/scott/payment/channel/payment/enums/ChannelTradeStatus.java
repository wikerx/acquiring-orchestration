package com.scott.payment.channel.payment.enums;

import lombok.Getter;

/**
 * 渠道trade状态枚举，位于 渠道适配库，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 */
@Getter
public enum ChannelTradeStatus {

    /**
     * SUCCESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    SUCCESS("SUCCESS"),

    /**
     * 渠道已完成授权；是否为平台终态由交易类型决定。
     */
    AUTHORIZED("AUTHORIZED"),

    /**
     * 渠道已确认资金捕获，可由平台状态机推进对应交易。
     */
    CAPTURED("CAPTURED"),

    /**
     * 渠道已确认退款完成；只允许退款动作据此进入成功终态。
     */
    REFUNDED("REFUNDED"),

    /**
     * 渠道已确认撤销或授权取消完成；只允许平台 VOID 动作据此进入成功终态。
     */
    VOIDED("VOIDED"),

    /**
     * FAILED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    FAILED("FAILED"),

    /**
     * PENDING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PENDING("PENDING"),

    /**
     * PROCESSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PROCESSING("PROCESSING"),

    /**
     * NEED REDIRECT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    NEED_REDIRECT("NEED_REDIRECT");


    private final String code;

    /**
     * 绑定统一渠道状态与稳定协议编码。
     *
     * @param code 状态编码
     */
    ChannelTradeStatus(String code) {
        this.code = code;
    }
}
