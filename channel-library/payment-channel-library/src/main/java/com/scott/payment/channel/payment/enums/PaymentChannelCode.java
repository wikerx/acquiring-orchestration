package com.scott.payment.channel.payment.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCode
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道编码枚举，位于 payment-channel-library 枚举层，用于收敛平台内置渠道编码，后台渠道配置仍以数据库 channel_code 为准。
 * @status : create
 */
@Getter
public enum PaymentChannelCode {

    /**
     * Mastercard Payment Gateway Services。
     */
    MPGS("MPGS");

    private final String code;

    PaymentChannelCode(String code) {
        this.code = code;
    }
}
