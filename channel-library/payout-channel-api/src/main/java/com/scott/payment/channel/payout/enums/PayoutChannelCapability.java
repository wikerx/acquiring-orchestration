package com.scott.payment.channel.payout.enums;

import lombok.Getter;

/**
 * 代付 Provider 能力枚举，避免与收单 Payment/Refund/Capture 能力混用。
 */
@Getter
public enum PayoutChannelCapability {

    /**
     * SUBMIT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    SUBMIT("SUBMIT"),
    /**
     * QUERY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    QUERY("QUERY"),

    /**
     * CANCEL 能力，表示 Provider 支持取消仍在处理中的代付。
     */
    CANCEL("CANCEL");

    private final String code;

    PayoutChannelCapability(String code) {
        this.code = code;
    }
}
