package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelUnsupportedOperationException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道不支持交易能力异常，位于 payment-channel-library 异常层，用于阻止不具备能力的渠道被错误调用。
 * @status : create
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
