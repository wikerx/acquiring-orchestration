package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelRequestException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道请求异常，位于 payment-channel-library 异常层，用于表达渠道 HTTP 调用、认证或网络请求失败。
 * @status : create
 */
public class ChannelRequestException extends ChannelException {

    public ChannelRequestException(String message) {
        super(message);
    }

    public ChannelRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
