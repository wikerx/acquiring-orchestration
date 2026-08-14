package com.scott.payment.channel.payout.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelStatus
 * @date : 2026-08-12 00:00
 * @description : Provider 原始状态映射后的统一代付状态，不等同于 service-payout 平台状态机状态。
 * @status : create
 */
@Getter
public enum PayoutChannelStatus {

    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    RETURNED("RETURNED");

    private final String code;

    PayoutChannelStatus(String code) {
        this.code = code;
    }
}
