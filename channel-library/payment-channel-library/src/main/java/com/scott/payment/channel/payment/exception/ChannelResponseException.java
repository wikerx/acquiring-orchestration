package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelResponseException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道响应异常，位于 payment-channel-library 异常层，用于表达渠道响应缺失、格式错误或状态映射失败。
 * @status : create
 */
public class ChannelResponseException extends ChannelException {

    public ChannelResponseException(String message) {
        super(message);
    }

    public ChannelResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
