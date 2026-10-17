package com.scott.payment.channel.payment.dto.callback;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackAcknowledgement
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调成功确认契约，位于 payment-channel-api DTO 层，只能在 Inbox 可靠受理后用于生成渠道要求的 2xx 响应。
 * @status : create
 */
public record ChannelCallbackAcknowledgement(int statusCode,
                                             String contentType,
                                             String body,
                                             BodyMode bodyMode) {

    /** 响应体是渠道原文还是平台统一成功信封。 */
    public enum BodyMode {
        /** 直接返回渠道要求的原始响应体。 */
        RAW,
        /** 使用平台统一成功响应包装 body。 */
        PLATFORM_SUCCESS_ENVELOPE
    }

    public ChannelCallbackAcknowledgement {
        if (statusCode < 200 || statusCode > 299) {
            throw new IllegalArgumentException("callback acknowledgement must use a 2xx status");
        }
        bodyMode = bodyMode == null ? BodyMode.RAW : bodyMode;
    }

    /** 创建 HTTP 200 的纯文本渠道确认。 */
    public static ChannelCallbackAcknowledgement plainText(String body) {
        return new ChannelCallbackAcknowledgement(200, "text/plain", body, BodyMode.RAW);
    }

    /** 创建 HTTP 200 的平台统一 JSON 成功确认。 */
    public static ChannelCallbackAcknowledgement platformSuccess(String message) {
        return new ChannelCallbackAcknowledgement(
                200, "application/json", message, BodyMode.PLATFORM_SUCCESS_ENVELOPE);
    }
}
