package com.scott.payment.channel.payout.enums;

import lombok.Getter;

/**
 * 代付 Provider 能力枚举，避免与收单 Payment/Refund/Capture 能力混用。
 */
@Getter
public enum PayoutChannelCapability {

    /**
     * SUBMIT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    SUBMIT("SUBMIT"),
    /**
     * QUERY 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    QUERY("QUERY"),

    /**
     * CANCEL 能力，表示 Provider 支持取消仍在处理中的代付。
     */
    CANCEL("CANCEL");

    private final String code;

    /**
     * 绑定代付 Provider 能力与稳定协议编码。
     *
     * @param code 能力编码
     */
    PayoutChannelCapability(String code) {
        this.code = code;
    }
}
