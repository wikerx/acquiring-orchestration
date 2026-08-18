package com.scott.payment.channel.payment.api;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.exception.ChannelUnsupportedOperationException;
import com.scott.payment.channel.payment.enums.ChannelCapability;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackHandler
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道回调处理 SPI，位于 payment-channel-api API 层，用于隔离渠道验签、解析和状态映射差异；平台幂等和状态机仍由 service-payment 负责。
 * @status : create
 */
public interface PaymentChannelCallbackHandler {

    /**
     * 获取渠道编码。
     *
     * @return 渠道编码
     */
    String channelCode();

    /**
     * 解析渠道回调。
     *
     * @param request 渠道回调请求
     * @return 渠道回调解析结果
     */
    default ChannelCallbackResult handle(ChannelCallbackRequest request) {
        throw new ChannelUnsupportedOperationException(channelCode(), ChannelCapability.QUERY.getCode());
    }
}
