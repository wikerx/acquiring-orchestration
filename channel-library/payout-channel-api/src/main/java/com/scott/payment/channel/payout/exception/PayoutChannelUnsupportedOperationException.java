package com.scott.payment.channel.payout.exception;

/**
 * Provider 未声明或未实现指定代付能力时抛出的稳定异常。
 */
public class PayoutChannelUnsupportedOperationException extends PayoutChannelException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建包含渠道编码和不支持能力的稳定异常。
     *
     * @param channelCode 渠道编码
     * @param capability  请求能力编码
     */
    public PayoutChannelUnsupportedOperationException(String channelCode, String capability) {
        super("当前代付渠道[" + channelCode + "]不支持能力[" + capability + "]");
    }
}
