package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelRequestException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道请求异常，位于 payment-channel-api 异常层，用于表达发送前校验、配置认证或网络调用失败，并显式区分渠道结果是否不确定。
 * @status : create
 */
public class ChannelRequestException extends ChannelException {

    /**
     * 创建确定性渠道请求异常，适用于发送前参数、配置或认证校验失败。
     *
     * @param message 异常信息
     */
    public ChannelRequestException(String message) {
        super(message);
    }

    /**
     * 创建默认确定性的渠道请求异常。
     *
     * @param message 异常信息
     * @param cause   原始异常
     */
    public ChannelRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建带渠道结果确定性语义的请求异常，网络或中断异常应显式传入 true。
     *
     * @param message          异常信息
     * @param cause            原始异常
     * @param outcomeUncertain true 表示请求可能已经到达渠道
     */
    public ChannelRequestException(String message, Throwable cause, boolean outcomeUncertain) {
        super(message, cause, outcomeUncertain);
    }
}
