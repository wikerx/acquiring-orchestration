package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
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
     * 安全拦截事件记录器，仅记录脱敏排查元数据。
     */
    private final SecurityInterceptEventRecorder securityInterceptEventRecorder;

    /**
     * 创建回调入口安全校验组件。
     *
     * @param callbackProperties 回调安全配置
     * @param securityInterceptEventRecorder 安全拦截事件记录器
     */
    public OpenApiCallbackSecuritySupport(OpenApiCallbackProperties callbackProperties,
                                          SecurityInterceptEventRecorder securityInterceptEventRecorder) {
        this.callbackProperties = callbackProperties;
        this.securityInterceptEventRecorder = securityInterceptEventRecorder;
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
            throw recordAndReturnOpenApiException(request,
                    "OPENAPI_NOTIFY_RETRY_TOKEN_INVALID",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "OPENAPI_NOTIFY_RETRY_TOKEN",
                    "merchant notify retry token is invalid");
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
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_HEADER_MISSING",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback signature headers are required");
        }
        long timestamp;
        try {
            timestamp = parseTimestamp(timestampText);
        } catch (ApiException exception) {
            securityInterceptEventRecorder.recordBlocked(
                    request,
                    SecurityInterceptEventRecorder.SOURCE_CHANNEL,
                    "CHANNEL_SIGNATURE_TIMESTAMP_INVALID",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    null,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    securityInterceptEventRecorder.reasonCode(exception),
                    securityInterceptEventRecorder.reasonMessage(exception)
            );
            throw exception;
        }
        if (Math.abs(InternalServiceSignature.currentTimeMillis() - timestamp) > callbackProperties.getAllowedClockSkewMillis()) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_TIMESTAMP_EXPIRED",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback signature timestamp is expired");
        }
        String normalizedChannelCode = channelCode.toLowerCase(Locale.ROOT);
        String channelSecret = callbackProperties.getChannelSecrets().get(channelCode);
        if (!StringUtils.hasText(channelSecret)) {
            channelSecret = callbackProperties.getChannelSecrets().get(normalizedChannelCode);
        }
        if (!StringUtils.hasText(channelSecret)) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_CALLBACK_SECRET_MISSING",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback secret is not configured");
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
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_INVALID",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback signature is invalid");
        }
        return new CallbackSecurityResult(true, true);
    }

/**
 * 写入或更新 record And Return Open Api Exception 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @param eventType event Type 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskLevel risk Level 输入值，含义由调用方法名称和所属业务对象限定
 * @param hitRuleCode hit Rule Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 当前方法计算或转换后的业务结果
 */
    private ApiException recordAndReturnOpenApiException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message) {
        ApiException exception = new ApiException(ApiResultEnum.UNAUTHORIZED, message);
        securityInterceptEventRecorder.recordBlocked(
                request,
                SecurityInterceptEventRecorder.SOURCE_OPENAPI,
                eventType,
                riskLevel,
                null,
                hitRuleCode,
                securityInterceptEventRecorder.reasonCode(exception),
                securityInterceptEventRecorder.reasonMessage(exception)
        );
        return exception;
    }

/**
 * 写入或更新 record And Return Channel Exception 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @param eventType event Type 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskLevel risk Level 输入值，含义由调用方法名称和所属业务对象限定
 * @param hitRuleCode hit Rule Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
 * @return 当前方法计算或转换后的业务结果
 */
    private ApiException recordAndReturnChannelException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message) {
        ApiException exception = new ApiException(ApiResultEnum.UNAUTHORIZED, message);
        securityInterceptEventRecorder.recordBlocked(
                request,
                SecurityInterceptEventRecorder.SOURCE_CHANNEL,
                eventType,
                riskLevel,
                null,
                hitRuleCode,
                securityInterceptEventRecorder.reasonCode(exception),
                securityInterceptEventRecorder.reasonMessage(exception)
        );
        return exception;
    }

    /**
     * 解析 parse Timestamp 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param timestampText 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 解析后的内部数据结构或业务值
     */
    private long parseTimestamp(String timestampText) {
        try {
            return Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature timestamp is invalid");
        }
    }

    /**
     * 完成 sha256 Hex 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawBody raw Body 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
