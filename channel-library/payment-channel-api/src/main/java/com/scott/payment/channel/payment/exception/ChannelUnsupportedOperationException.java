package com.scott.payment.channel.payment.exception;

/**
 * 渠道不支持交易能力异常，位于 payment-channel-api 异常层，用于阻止不具备能力的渠道被错误调用。
 */
public class ChannelUnsupportedOperationException extends ChannelException {

    /**
     * 创建渠道不支持交易能力异常。
     *
     * @param channelCode 渠道编码
     * @param capability  交易能力
     */
    public ChannelUnsupportedOperationException(String channelCode, String capability) {
        super("当前渠道[" + channelCode + "]不支持交易能力[" + capability + "]");
    }
}
