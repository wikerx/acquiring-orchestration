package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.exception.ChannelRequestException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayChannelNotImplementedException
 * @date : 2026-07-19 23:15
 * @email : scott_x@163.com
 * @description : WorldPay 渠道真实请求未实现异常，位于 payment-channel-worldpay 渠道实现层，用于明确 WPGXML/WPGJSON 当前只完成 SPI 骨架和回调解析，尚不能发起生产交易。
 * @status : create
 */
public class WorldPayChannelNotImplementedException extends ChannelRequestException {

    /**
     * 创建 WorldPay 渠道真实请求未实现异常。
     *
     * @param channelCode 独立渠道编码，例如 WPGXML 或 WPGJSON
     * @param transactionType 平台交易类型
     */
    public WorldPayChannelNotImplementedException(String channelCode, String transactionType) {
        super("WorldPay渠道[" + channelCode + "]真实请求尚未接通，当前仅完成SPI骨架、回调解析和平台状态映射，禁止用于生产交易能力[" + transactionType + "]");
    }
}
