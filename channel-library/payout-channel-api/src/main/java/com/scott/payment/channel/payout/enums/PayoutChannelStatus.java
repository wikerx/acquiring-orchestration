package com.scott.payment.channel.payout.enums;

import lombok.Getter;

/**
 * Provider 原始状态映射后的统一代付状态，不等同于 service-payout 平台状态机状态。
 */
@Getter
public enum PayoutChannelStatus {

    /**
     * SUCCESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    SUCCESS("SUCCESS"),
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
     * RETURNED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RETURNED("RETURNED");

    private final String code;

    /**
     * 绑定统一代付渠道状态与稳定协议编码。
     *
     * @param code 状态编码
     */
    PayoutChannelStatus(String code) {
        this.code = code;
    }
}
