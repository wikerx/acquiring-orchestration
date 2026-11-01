package com.scott.payment.channel.payment.exception;

/**
 * 渠道超时异常，位于 payment-channel-api 异常层，用于表达渠道请求超时且结果未知的场景。
 */
public class ChannelTimeoutException extends ChannelException {

    /**
     * 创建渠道超时异常。超时无法证明渠道未受理，因此结果固定为不确定。
     *
     * @param message 异常信息
     */
    public ChannelTimeoutException(String message) {
        super(message, null, true);
    }

    /**
     * 创建带原始异常的渠道超时异常，结果固定为不确定。
     *
     * @param message 异常信息
     * @param cause   原始异常
     */
    public ChannelTimeoutException(String message, Throwable cause) {
        super(message, cause, true);
    }
}
