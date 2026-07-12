package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道异常基类，位于 payment-channel-library 异常层，用于封装渠道注册、执行、请求和响应映射中的可归因异常。
 * @status : create
 */
public class ChannelException extends RuntimeException {

    /**
     * 创建渠道异常。
     *
     * @param message 异常信息
     */
    public ChannelException(String message) {
        super(message);
    }

    /**
     * 创建渠道异常。
     *
     * @param message 异常信息
     * @param cause   原始异常
     */
    public ChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
