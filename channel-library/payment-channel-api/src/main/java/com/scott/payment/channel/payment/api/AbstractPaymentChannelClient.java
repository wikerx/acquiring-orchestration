package com.scott.payment.channel.payment.api;

import com.scott.payment.channel.payment.enums.ChannelCapability;

/**
 * 收单渠道客户端抽象基类，位于 payment-channel-api API 层，为渠道实现提供能力校验等通用约束。
 */
public abstract class AbstractPaymentChannelClient implements PaymentChannelClient {

    /**
     * 校验当前渠道支持指定能力。
     *
     * @param capability 渠道能力
     */
    protected void requireCapability(ChannelCapability capability) {
        if (!supports(capability)) {
            throw unsupported(capability);
        }
    }
}
