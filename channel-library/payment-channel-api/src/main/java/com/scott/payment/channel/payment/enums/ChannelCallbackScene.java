package com.scott.payment.channel.payment.enums;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackScene
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调入口场景枚举，位于 payment-channel-api 契约层，在渠道解析前区分资金回调与 3DS 认证回调。
 * @status : create
 */
public enum ChannelCallbackScene {
    /** 普通渠道交易或退款回调。 */
    STANDARD,
    /** 3DS Method、Challenge 或认证结果回调。 */
    THREE_DS
}
