package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道异常基类，位于 payment-channel-api 异常层，用于封装渠道注册、执行、请求和响应映射中的可归因异常。
 * @status : create
 */
public class ChannelException extends RuntimeException {

    /**
     * 渠道结果是否不确定。true 表示请求可能已经到达渠道，平台不得直接认定资金动作失败。
     */
    private final boolean outcomeUncertain;

    /**
     * 创建渠道异常。
     *
     * @param message 异常信息
     */
    public ChannelException(String message) {
        this(message, null, false);
    }

    /**
     * 创建渠道异常。
     *
     * @param message 异常信息
     * @param cause   原始异常
     */
    public ChannelException(String message, Throwable cause) {
        this(message, cause, false);
    }

    /**
     * 创建带渠道结果确定性语义的异常。
     *
     * @param message          异常信息，不得包含渠道凭据或完整支付敏感数据
     * @param cause            原始异常，可为空
     * @param outcomeUncertain true 表示请求可能已被渠道受理，需要查询或回调勾兑
     */
    public ChannelException(String message, Throwable cause, boolean outcomeUncertain) {
        super(message, cause);
        this.outcomeUncertain = outcomeUncertain;
    }

    /**
     * 判断渠道资金动作结果是否仍需勾兑。
     *
     * @return true 表示不能直接失败或自动重发，false 表示异常发生在可确定未受理的阶段
     */
    public boolean isOutcomeUncertain() {
        return outcomeUncertain;
    }
}
