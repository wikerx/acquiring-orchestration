package com.scott.payment.channel.payment.api;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackVerifier
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道回调验签 SPI，由渠道模块声明支持的渠道编码并校验渠道原始回调，不处理平台交易状态。
 * @status : create
 */
public interface PaymentChannelCallbackVerifier {

    /**
     * 返回当前 verifier 支持的渠道编码；空集合表示协议中立的兼容回退实现。
     *
     * @return 大写渠道编码集合
     */
    Set<String> channelCodes();

    /**
     * 校验渠道回调签名，失败时抛出稳定分类异常。
     *
     * @param request 回调方法、路径、请求头、原文和密钥上下文
     */
    void verify(ChannelCallbackVerificationRequest request);
}
