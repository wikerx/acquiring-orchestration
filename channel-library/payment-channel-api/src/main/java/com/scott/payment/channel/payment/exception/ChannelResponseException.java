package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelResponseException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道响应异常，位于 payment-channel-api 异常层，用于表达响应缺失、格式错误或渠道明确拒绝；默认结果未知，适配器可对明确拒绝显式标记为确定失败。
 * @status : create
 */
public class ChannelResponseException extends ChannelException {

    /**
     * 创建渠道响应异常。请求已经发出但响应不可用，结果固定为不确定。
     *
     * @param message 异常信息
     */
    public ChannelResponseException(String message) {
        super(message, null, true);
    }

    /**
     * 创建带原始异常的渠道响应异常，结果固定为不确定。
     *
     * @param message 异常信息
     * @param cause   原始异常
     */
    public ChannelResponseException(String message, Throwable cause) {
        super(message, cause, true);
    }

    /**
     * 创建带明确结果确定性语义的渠道响应异常。
     *
     * @param message          异常信息，不得包含渠道凭据或完整支付敏感数据
     * @param cause            原始异常，可为空
     * @param outcomeUncertain true 表示渠道响应不足以判断请求结果，false 表示渠道已明确拒绝请求
     */
    public ChannelResponseException(String message, Throwable cause, boolean outcomeUncertain) {
        super(message, cause, outcomeUncertain);
    }
}
