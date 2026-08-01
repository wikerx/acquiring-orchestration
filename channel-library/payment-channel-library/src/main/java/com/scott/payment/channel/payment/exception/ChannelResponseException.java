package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelResponseException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道响应异常，位于 payment-channel-library 异常层，用于表达已发出请求的响应缺失、格式错误或状态映射失败，此时资金结果需要后续勾兑。
 * @status : create
 */
public class ChannelResponseException extends ChannelException {

    /**
     * 创建渠道响应异常。请求已经发出但响应不可用，结果固定为不确定。
     *
     * @param message 异常信息
     */
    public ChannelResponseException(String message) {
        super(message, null, true);
    }

    /**
     * 创建带原始异常的渠道响应异常，结果固定为不确定。
     *
     * @param message 异常信息
     * @param cause   原始异常
     */
    public ChannelResponseException(String message, Throwable cause) {
        super(message, cause, true);
    }
}
