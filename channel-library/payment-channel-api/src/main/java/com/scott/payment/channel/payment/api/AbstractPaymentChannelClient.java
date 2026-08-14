package com.scott.payment.channel.payment.api;

import com.scott.payment.channel.payment.enums.ChannelCapability;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AbstractPaymentChannelClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道客户端抽象基类，位于 payment-channel-api API 层，为渠道实现提供能力校验等通用约束。
 * @status : create
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
