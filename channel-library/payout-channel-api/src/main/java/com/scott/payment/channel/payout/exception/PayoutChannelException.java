package com.scott.payment.channel.payout.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelException
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 代付渠道层基础异常，不复用收单渠道异常层级。
 * @status : create
 */
public class PayoutChannelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造不携带底层原因的代付渠道异常。
     *
     * @param message 已完成敏感信息控制的渠道失败说明
     */
    public PayoutChannelException(String message) {
        this(message, null);
    }

    /**
     * 构造携带底层原因的代付渠道异常。
     *
     * @param message 已完成敏感信息控制的渠道失败说明
     * @param cause 底层异常，仅供服务端诊断，不得直接返回调用方
     */
    public PayoutChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
