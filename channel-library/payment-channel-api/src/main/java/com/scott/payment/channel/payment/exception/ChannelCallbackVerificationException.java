package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackVerificationException
 * @date : 2026-08-12 00:00
 * @description : 渠道回调验签失败异常，向入口层提供稳定原因分类，不包含密钥、签名原文或敏感报文。
 * @status : create
 */
public class ChannelCallbackVerificationException extends ChannelException {

    private final Reason reason;

    public ChannelCallbackVerificationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ChannelCallbackVerificationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /** 不暴露协议细节的稳定验签失败分类。 */
    public enum Reason {
        HEADER_MISSING,
        HEADER_INVALID,
        TIMESTAMP_INVALID,
        TIMESTAMP_EXPIRED,
        SECRET_MISSING,
        ALGORITHM_UNSUPPORTED,
        SIGNATURE_INVALID,
        INTERNAL_ERROR
    }
}
