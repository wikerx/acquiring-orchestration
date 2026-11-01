package com.scott.payment.channel.payment.enums;

/**
 * 渠道回调场景枚举，位于 渠道适配库，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 */
public enum ChannelCallbackScene {
    /**
     * 普通渠道交易或退款回调。
     */
    STANDARD,
    /**
     * 3DS Method、Challenge 或认证结果回调。
     */
    THREE_DS
}
