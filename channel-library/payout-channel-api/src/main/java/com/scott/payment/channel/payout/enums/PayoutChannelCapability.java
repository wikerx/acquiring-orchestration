package com.scott.payment.channel.payout.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelCapability
 * @date : 2026-08-12 00:00
 * @description : 代付 Provider 能力枚举，避免与收单 Payment/Refund/Capture 能力混用。
 * @status : create
 */
@Getter
public enum PayoutChannelCapability {

    SUBMIT("SUBMIT"),
    QUERY("QUERY");

    private final String code;

    PayoutChannelCapability(String code) {
        this.code = code;
    }
}
