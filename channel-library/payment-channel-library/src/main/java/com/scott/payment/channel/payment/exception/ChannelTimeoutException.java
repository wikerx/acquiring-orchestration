package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTimeoutException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道超时异常，位于 payment-channel-library 异常层，用于表达渠道请求超时且结果未知的场景。
 * @status : create
 */
public class ChannelTimeoutException extends ChannelException {

    public ChannelTimeoutException(String message) {
        super(message);
    }

    public ChannelTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
