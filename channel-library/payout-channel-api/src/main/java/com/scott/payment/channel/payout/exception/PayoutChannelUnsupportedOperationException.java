package com.scott.payment.channel.payout.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelUnsupportedOperationException
 * @date : 2026-08-12 00:00
 * @description : Provider 未声明或未实现指定代付能力时抛出的稳定异常。
 * @status : create
 */
public class PayoutChannelUnsupportedOperationException extends PayoutChannelException {

    private static final long serialVersionUID = 1L;

    public PayoutChannelUnsupportedOperationException(String channelCode, String capability) {
        super("当前代付渠道[" + channelCode + "]不支持能力[" + capability + "]");
    }
}
