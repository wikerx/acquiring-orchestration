package com.scott.payment.channel.payment.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTradeStatus
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道统一交易状态枚举，位于 payment-channel-library 枚举层，用于将渠道原始状态映射为平台可理解的渠道结果，不直接更新平台 transaction_status。
 * @status : create
 */
@Getter
public enum ChannelTradeStatus {

    SUCCESS("SUCCESS"),

    FAILED("FAILED"),

    PENDING("PENDING"),

    PROCESSING("PROCESSING"),

    NEED_REDIRECT("NEED_REDIRECT");

    private final String code;

    ChannelTradeStatus(String code) {
        this.code = code;
    }
}
