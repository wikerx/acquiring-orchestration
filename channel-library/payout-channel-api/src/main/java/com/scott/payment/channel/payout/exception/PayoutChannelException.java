package com.scott.payment.channel.payout.exception;

/**
 * 代付渠道异常，显式区分确定失败与需要查询勾兑的未知结果。
 **/
public class PayoutChannelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道结果是否仍不确定。
     *
     * <p>{@code true} 表示请求可能已经到达渠道，平台不得直接置为失败或自动重发。</p>
     **/
    private final boolean outcomeUncertain;

    /**
     * 创建结果确定的代付渠道异常。
     *
     * @param message 已完成敏感信息控制的渠道失败说明
     **/
    public PayoutChannelException(String message) {
        this(message, null, false);
    }

    /**
     * 创建携带底层原因且结果确定的代付渠道异常。
     *
     * @param message 已完成敏感信息控制的渠道失败说明
     * @param cause 底层异常，仅供服务端诊断，不得直接返回调用方
     **/
    public PayoutChannelException(String message, Throwable cause) {
        this(message, cause, false);
    }

    /**
     * 创建携带渠道结果确定性语义的代付渠道异常。
     *
     * @param message 已完成敏感信息控制的渠道失败说明
     * @param cause 底层异常，仅供服务端诊断，不得直接返回调用方
     * @param outcomeUncertain true 表示请求可能已被渠道受理，不能直接失败或自动重发
     **/
    public PayoutChannelException(String message, Throwable cause, boolean outcomeUncertain) {
        super(message, cause);
        this.outcomeUncertain = outcomeUncertain;
    }

    /**
     * 判断当前异常是否需要通过查询或回调继续勾兑。
     *
     * @return true 表示结果未知，false 表示异常发生在可确定未受理的阶段
     **/
    public boolean isOutcomeUncertain() {
        return outcomeUncertain;
    }
}
