package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiCallbackSecuritySupport
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 回调类入口安全校验组件，负责渠道回调签名和商户通知重试维护密钥校验。
 * @status : create
 */
@Component
public class OpenApiCallbackSecuritySupport {

    /**
     * 渠道回调时间戳请求头。
     */
    public static final String CHANNEL_TIMESTAMP_HEADER = "X-Channel-Timestamp";

    /**
     * 渠道回调随机串请求头。
     */
    public static final String CHANNEL_NONCE_HEADER = "X-Channel-Nonce";

    /**
     * 渠道回调签名请求头。
     */
    public static final String CHANNEL_SIGNATURE_HEADER = "X-Channel-Signature";

    /**
     * 商户通知重试维护密钥请求头。
     */
    public static final String NOTIFY_RETRY_TOKEN_HEADER = "X-Notify-Retry-Token";

    /**
     * 回调安全配置。
     */
    private final OpenApiCallbackProperties callbackProperties;

    /**
     * 创建回调入口安全校验组件。
     *
     * @param callbackProperties 回调安全配置
     */
    public OpenApiCallbackSecuritySupport(OpenApiCallbackProperties callbackProperties) {
        this.callbackProperties = callbackProperties;
    }

    /**
     * 校验商户通知重试维护密钥，避免外部直接触发通知重试。
     *
     * @param request HTTP 请求
     */
    public void verifyNotifyRetryToken(HttpServletRequest request) {
        String expectedToken = callbackProperties.getNotifyRetryToken();
        String actualToken = request.getHeader(NOTIFY_RETRY_TOKEN_HEADER);
        if (!StringUtils.hasText(expectedToken)
                || !InternalServiceSignature.matches(expectedToken, actualToken)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant notify retry token is invalid");
        }
    }

    /**
     * 校验渠道回调签名。
     * <p>
     * 当前占位入口尚未接入渠道原文落库和业务幂等，只先建立渠道维度签名边界；后续接入真实渠道回调时，
     * 应将原始 body 摘要纳入签名文本，并保存原文与幂等键。
     *
     * @param channelCode 渠道编码
     * @param request     HTTP 请求
     */
    public void verifyChannelCallback(String channelCode, HttpServletRequest request) {
        if (!callbackProperties.isChannelSignatureRequired()) {
            return;
        }
        String timestampText = request.getHeader(CHANNEL_TIMESTAMP_HEADER);
        String nonce = request.getHeader(CHANNEL_NONCE_HEADER);
        String signature = request.getHeader(CHANNEL_SIGNATURE_HEADER);
        if (!StringUtils.hasText(channelCode)
                || !StringUtils.hasText(timestampText)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature headers are required");
        }
        long timestamp = parseTimestamp(timestampText);
        if (Math.abs(InternalServiceSignature.currentTimeMillis() - timestamp) > callbackProperties.getAllowedClockSkewMillis()) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature timestamp is expired");
        }
        String normalizedChannelCode = channelCode.toLowerCase(Locale.ROOT);
        String channelSecret = callbackProperties.getChannelSecrets().get(channelCode);
        if (!StringUtils.hasText(channelSecret)) {
            channelSecret = callbackProperties.getChannelSecrets().get(normalizedChannelCode);
        }
        if (!StringUtils.hasText(channelSecret)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback secret is not configured");
        }
        String expectedSignature = InternalServiceSignature.sign(
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                nonce,
                normalizedChannelCode,
                channelSecret
        );
        if (!InternalServiceSignature.matches(expectedSignature, signature)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature is invalid");
        }
    }

    private long parseTimestamp(String timestampText) {
        try {
            return Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature timestamp is invalid");
        }
    }
}
