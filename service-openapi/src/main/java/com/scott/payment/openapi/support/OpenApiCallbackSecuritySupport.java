package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
     * 签名文本包含 method、path、timestamp、nonce、channelCode 和原始 body 的 SHA-256 摘要，避免回调
     * 业务报文被篡改后仍通过路径级签名。
     *
     * @param channelCode 渠道编码
     * @param request     HTTP 请求
     * @param rawBody     渠道回调原始 body，按 UTF-8 计算 SHA-256 摘要
     */
    public CallbackSecurityResult verifyChannelCallback(String channelCode, HttpServletRequest request, String rawBody) {
        if (!callbackProperties.isChannelSignatureRequired()) {
            return new CallbackSecurityResult(true, true);
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
                sha256Hex(rawBody),
                channelSecret
        );
        if (!InternalServiceSignature.matches(expectedSignature, signature)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature is invalid");
        }
        return new CallbackSecurityResult(true, true);
    }

    private long parseTimestamp(String timestampText) {
        try {
            return Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature timestamp is invalid");
        }
    }

    private String sha256Hex(String rawBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR, "channel callback body digest can not be calculated");
        }
    }

    /**
     * 渠道回调入口安全校验结果。
     *
     * @param signatureValid 签名校验是否通过
     * @param ipAllowed IP 白名单是否通过；当前配置尚未启用独立 IP 规则时按通过记录
     */
    public record CallbackSecurityResult(boolean signatureValid, boolean ipAllowed) {
    }
}
