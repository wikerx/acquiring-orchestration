package com.scott.payment.channel.payout.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelException
 * @date : 2026-08-12 00:00
 * @description : 代付渠道层基础异常，不复用收单渠道异常层级。
 * @status : create
 */
public class PayoutChannelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PayoutChannelException(String message) {
        super(message);
    }

    public PayoutChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
