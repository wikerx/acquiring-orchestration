package com.scott.payment.openapi.support;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackAcknowledgement;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.VerifiedChannelEvent;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.registry.PaymentChannelCallbackProtocolRegistry;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.component.web.gateway.TrustedClientIpResolver;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OpenAPI 回调类入口安全校验组件，负责渠道回调签名和商户通知重试维护密钥校验。
 */
@Component
public class OpenApiCallbackSecuritySupport {

    /**
     * 渠道回调验签最多接收的请求头数量，防止异常请求消耗过多内存。
     */
    private static final int MAX_VERIFICATION_HEADER_COUNT = 128;
    /**
     * 单个渠道回调请求头名称允许的最大字符数。
     */
    private static final int MAX_VERIFICATION_HEADER_NAME_LENGTH = 128;
    /**
     * 单个渠道回调请求头值允许的最大 UTF-8 字节数。
     */
    private static final int MAX_VERIFICATION_HEADER_VALUE_LENGTH = 4096;

    /**
     * 渠道回调时间戳请求头。
     */
    public static final String CHANNEL_TIMESTAMP_HEADER = "X-Callback-Timestamp";

    /**
     * 渠道回调随机串请求头。
     */
    public static final String CHANNEL_NONCE_HEADER = "X-Callback-Nonce";

    /**
     * 渠道回调签名请求头。
     */
    public static final String CHANNEL_SIGNATURE_HEADER = "X-Callback-Signature";

    /**
     * 渠道事件签名请求头，格式为 keyId/SHA256/signature。
     */
    public static final String CHANNEL_EVENT_SIGNATURE_HEADER = "Event-Signature";

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
     * 按渠道编码定位 provider 验签实现。
     */
    private final PaymentChannelCallbackProtocolRegistry callbackProtocolRegistry;

    /**
     * MID 级回调密钥解析器，优先于环境级渠道兜底配置。
     */
    private final ChannelCallbackSecretResolver channelCallbackSecretResolver;

    /**
     * 创建回调入口安全校验组件。
     *
     * @param callbackProperties 回调安全配置
     * @param securityInterceptEventRecorder 安全拦截事件记录器
     */
    @Autowired
    public OpenApiCallbackSecuritySupport(OpenApiCallbackProperties callbackProperties,
                                          SecurityInterceptEventRecorder securityInterceptEventRecorder,
                                          PaymentChannelCallbackProtocolRegistry callbackProtocolRegistry,
                                          ChannelCallbackSecretResolver channelCallbackSecretResolver) {
        this.callbackProperties = callbackProperties;
        this.securityInterceptEventRecorder = securityInterceptEventRecorder;
        this.callbackProtocolRegistry = callbackProtocolRegistry;
        this.channelCallbackSecretResolver = channelCallbackSecretResolver;
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
    public CallbackSecurityResult verifyChannelCallback(String channelCode,
                                                        ChannelCallbackScene scene,
                                                        HttpServletRequest request,
                                                        byte[] rawBody) {
        boolean ipAllowed = verifyChannelIp(channelCode, request);
        try {
            RawChannelCallbackRequest callbackRequest = new RawChannelCallbackRequest(
                    channelCode,
                    scene,
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getQueryString(),
                    verificationHeaders(request),
                    rawBody,
                    resolveClientIp(request));
            PaymentChannelCallbackProtocol protocol = callbackProtocolRegistry.validateAndGet(callbackRequest);
            String credentialHint = protocol.credentialHint(callbackRequest);
            VerifiedChannelEvent event = protocol.verifyAndParse(
                    callbackRequest,
                    new ChannelCallbackVerificationContext(
                    resolveChannelSecret(channelCode, credentialHint),
                    channelEventSecrets(channelCode),
                    callbackProperties.getAllowedClockSkewMillis(),
                    InternalServiceSignature.currentTimeMillis()));
            ChannelCallbackAcknowledgement acknowledgement =
                    protocol.successAcknowledgement(callbackRequest, event);
            return new CallbackSecurityResult(true, ipAllowed, callbackRequest, event, acknowledgement);
        } catch (ChannelCallbackVerificationException exception) {
            throw mapVerificationException(request, exception,
                    StringUtils.hasText(request.getHeader(CHANNEL_EVENT_SIGNATURE_HEADER)));
        } catch (ChannelRequestException exception) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_CALLBACK_PROTOCOL_REJECTED",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "CHANNEL_CALLBACK_PROTOCOL",
                    "channel callback does not match the provider protocol",
                    OpenApiResultCode.PARAM_INVALID);
        } catch (com.scott.payment.channel.payment.exception.ChannelException exception) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_CALLBACK_PROTOCOL_MISSING",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "CHANNEL_CALLBACK_PROTOCOL",
                    "channel callback protocol is not configured");
        } catch (RuntimeException exception) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_CALLBACK_PROTOCOL_INTERNAL_ERROR",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "CHANNEL_CALLBACK_PROTOCOL",
                    "channel callback protocol processing failed",
                    OpenApiResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * MID 级回调只读取原交易 MID 的签名密钥，不回退到渠道级共享密钥。
     *
     * @param channelCode 渠道编码
     * @param merchantId 渠道回调中解析出的 MID
     * @return 对应 MID 的回调密钥；未提供 MID 时返回渠道级密钥
     */
    private String resolveChannelSecret(String channelCode, String merchantId) {
        return StringUtils.hasText(merchantId)
                ? channelCallbackSecretResolver.resolve(channelCode, merchantId)
                : channelSecret(channelCode);
    }

    /**
     * 收集数量、名称和值长度均受限的请求头，供渠道协议实现验签。
     *
     * <p>本方法不记录请求头；持久化审计只使用控制器生成的脱敏摘要。</p>
     *
     * @param request 渠道回调 HTTP 请求
     * @return 不可修改的多值请求头集合
     */
    private Map<String, List<String>> verificationHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        while (names.hasMoreElements()) {
            if (headers.size() >= MAX_VERIFICATION_HEADER_COUNT) {
                throw new ChannelRequestException("channel callback contains too many headers");
            }
            String name = names.nextElement();
            if (!StringUtils.hasText(name) || name.length() > MAX_VERIFICATION_HEADER_NAME_LENGTH) {
                throw new ChannelRequestException("channel callback contains an invalid header name");
            }
            Enumeration<String> values = request.getHeaders(name);
            List<String> collected = new ArrayList<>();
            while (values != null && values.hasMoreElements()) {
                String value = values.nextElement();
                if (value != null && value.getBytes(StandardCharsets.UTF_8).length
                        > MAX_VERIFICATION_HEADER_VALUE_LENGTH) {
                    throw new ChannelRequestException("channel callback contains an oversized header value");
                }
                collected.add(value);
            }
            headers.put(name, List.copyOf(collected));
        }
        return Collections.unmodifiableMap(headers);
    }

    /**
     * 将渠道插件验签失败映射为 OpenAPI 既有错误码，并保留安全审计分类。
     *
     * @param request HTTP 请求
     * @param exception 渠道插件返回的稳定失败原因
     * @param eventSignature 是否使用 Event-Signature 格式
     * @return 对外 API 异常
     */
    private ApiException mapVerificationException(HttpServletRequest request,
                                                   ChannelCallbackVerificationException exception,
                                                   boolean eventSignature) {
        String hitRuleCode = eventSignature ? "CHANNEL_EVENT_SIGNATURE" : "CHANNEL_CALLBACK_SIGNATURE";
        String eventType;
        String riskLevel;
        switch (exception.getReason()) {
            case HEADER_MISSING -> {
                eventType = "CHANNEL_SIGNATURE_HEADER_MISSING";
                riskLevel = SecurityInterceptEventRecorder.RISK_HIGH;
            }
            case HEADER_INVALID -> {
                eventType = "CHANNEL_SIGNATURE_HEADER_INVALID";
                riskLevel = SecurityInterceptEventRecorder.RISK_HIGH;
            }
            case TIMESTAMP_INVALID -> {
                eventType = "CHANNEL_SIGNATURE_TIMESTAMP_INVALID";
                riskLevel = SecurityInterceptEventRecorder.RISK_HIGH;
            }
            case TIMESTAMP_EXPIRED -> {
                eventType = "CHANNEL_SIGNATURE_TIMESTAMP_EXPIRED";
                riskLevel = SecurityInterceptEventRecorder.RISK_HIGH;
            }
            case SECRET_MISSING -> {
                eventType = "CHANNEL_CALLBACK_SECRET_MISSING";
                riskLevel = SecurityInterceptEventRecorder.RISK_CRITICAL;
            }
            case ALGORITHM_UNSUPPORTED -> {
                eventType = "CHANNEL_SIGNATURE_ALGORITHM_UNSUPPORTED";
                riskLevel = SecurityInterceptEventRecorder.RISK_HIGH;
            }
            case SIGNATURE_INVALID -> {
                eventType = "CHANNEL_SIGNATURE_INVALID";
                riskLevel = SecurityInterceptEventRecorder.RISK_CRITICAL;
            }
            case INTERNAL_ERROR -> {
                eventType = "CHANNEL_SIGNATURE_INTERNAL_ERROR";
                riskLevel = SecurityInterceptEventRecorder.RISK_CRITICAL;
            }
            default -> throw new IllegalStateException("unsupported callback verification reason: " + exception.getReason());
        }
        OpenApiResultCode result = exception.getReason() == ChannelCallbackVerificationException.Reason.INTERNAL_ERROR
                ? OpenApiResultCode.SYSTEM_ERROR
                : OpenApiResultCode.AUTHENTICATION_FAILED;
        return recordAndReturnChannelException(request, eventType, riskLevel, hitRuleCode,
                exception.getReason().name(), result);
    }

    /**
     * 读取渠道事件签名密钥映射。
     *
     * @param channelCode 渠道编码
     * @return keyId 到密钥的映射
     */
    private Map<String, String> channelEventSecrets(String channelCode) {
        Map<String, Map<String, String>> configured = callbackProperties.getChannelEventSecrets();
        if (configured == null || configured.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> secrets = configured.get(channelCode);
        if (secrets == null) {
            secrets = configured.get(normalizeConfigKey(channelCode));
        }
        if (secrets == null) {
            secrets = configured.get(normalizeChannelCode(channelCode));
        }
        return secrets == null ? Collections.emptyMap() : secrets;
    }

    /**
     * 按渠道读取平台自定义或回退签名密钥。
     *
     * @param channelCode 渠道编码
     * @return HMAC-SHA256 共享密钥
     */
    private String channelSecret(String channelCode) {
        Map<String, String> channelSecrets = callbackProperties.getChannelSecrets();
        if (channelSecrets == null || channelSecrets.isEmpty()) {
            return null;
        }
        String secret = channelSecrets.get(channelCode);
        if (!StringUtils.hasText(secret)) {
            secret = channelSecrets.get(normalizeConfigKey(channelCode));
        }
        if (!StringUtils.hasText(secret)) {
            secret = channelSecrets.get(normalizeChannelCode(channelCode));
        }
        return secret;
    }

    /**
     * 校验渠道回调源 IP。
     * <p>
     * 未配置渠道 IP 白名单时返回通过；配置后只接受精确 IPv4/IPv6 地址，防止伪造来源推进交易状态。
     *
     * @param channelCode 渠道编码
     * @param request HTTP 请求
     * @return true 表示来源 IP 允许访问
     */
    private boolean verifyChannelIp(String channelCode, HttpServletRequest request) {
        List<String> allowedIps = allowedIps(channelCode);
        if (allowedIps.isEmpty()) {
            if (callbackProperties.isChannelIpWhitelistRequired()) {
                throw recordAndReturnChannelException(request,
                        "CHANNEL_CALLBACK_IP_WHITELIST_MISSING",
                        SecurityInterceptEventRecorder.RISK_CRITICAL,
                        "CHANNEL_CALLBACK_IP",
                        "channel callback ip whitelist is required");
            }
            return true;
        }
        String clientIp = resolveClientIp(request);
        String normalizedClientIp = normalizeIp(clientIp);
        boolean matched = normalizedClientIp != null && allowedIps.stream()
                .map(this::normalizeIp)
                .anyMatch(normalizedClientIp::equals);
        if (!matched) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_CALLBACK_IP_NOT_ALLOWED",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "CHANNEL_CALLBACK_IP",
                    "channel callback source ip is not allowed");
        }
        return true;
    }

    /**
     * 读取渠道 IP 白名单配置。
     *
     * @param channelCode 渠道编码
     * @return 渠道允许 IP 列表
     */
    private List<String> allowedIps(String channelCode) {
        Map<String, List<String>> configured = callbackProperties.getChannelAllowedIps();
        if (configured == null || configured.isEmpty()) {
            return List.of();
        }
        List<String> allowedIps = configured.get(channelCode);
        if (allowedIps == null) {
            allowedIps = configured.get(normalizeConfigKey(channelCode));
        }
        if (allowedIps == null) {
            allowedIps = configured.get(normalizeChannelCode(channelCode));
        }
        return allowedIps == null ? List.of() : allowedIps.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    /**
     * 解析可信来源 IP。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    public String resolveClientIp(HttpServletRequest request) {
        return TrustedClientIpResolver.resolve(request);
    }

    /**
     * 将 IP 标准化为精确可比较值。
     *
     * @param ip 原始 IP
     * @return 标准化 IP，格式非法时为空
     */
    private String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        try {
            return IpAddressNormalizer.normalizeExact(ip).ipValue();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 将配置 key 规范化为小写。
     *
     * @param value 原始配置 key
     * @return 小写配置 key
     */
    private String normalizeConfigKey(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 将渠道编码规范化为大写。
     *
     * @param channelCode 原始渠道编码
     * @return 大写渠道编码
     */
    private String normalizeChannelCode(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 记录 OpenAPI 维护入口的认证拒绝并返回统一异常。
     *
     * @param request 当前 HTTP 请求
     * @param eventType 安全事件类型
     * @param riskLevel 风险等级
     * @param hitRuleCode 命中的安全规则
     * @param message 不含敏感数据的内部诊断摘要
     * @return 已完成审计记录的认证异常
     */
    private ApiException recordAndReturnOpenApiException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message) {
        ApiException exception = new ApiException(OpenApiResultCode.AUTHENTICATION_FAILED, message);
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
     * 以认证失败码记录渠道回调安全拒绝。
     *
     * @param request 当前 HTTP 请求
     * @param eventType 安全事件类型
     * @param riskLevel 风险等级
     * @param hitRuleCode 命中的渠道回调规则
     * @param message 不含回调原文的内部诊断摘要
     * @return 已完成审计记录的认证异常
     */
    private ApiException recordAndReturnChannelException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message) {
        return recordAndReturnChannelException(request, eventType, riskLevel, hitRuleCode,
                message, OpenApiResultCode.AUTHENTICATION_FAILED);
    }

    /**
     * 使用指定公开结果码记录渠道回调安全拒绝。
     *
     * @param request 当前 HTTP 请求
     * @param eventType 安全事件类型
     * @param riskLevel 风险等级
     * @param hitRuleCode 命中的渠道回调规则
     * @param message 不含回调原文的内部诊断摘要
     * @param result 统一 OpenAPI 结果码
     * @return 已完成审计记录的渠道回调异常
     */
    private ApiException recordAndReturnChannelException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message,
                                                         OpenApiResultCode result) {
        ApiException exception = new ApiException(result, message);
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
     * 渠道回调入口安全校验结果。
     *
     * @param signatureValid 签名校验是否通过
     * @param ipAllowed IP 白名单是否通过；未要求配置白名单时按通过记录
     * @param request 渠道协议验签使用的原始请求对象
     * @param event 已验签并标准化的渠道事件
     * @param acknowledgement 渠道协议要求的成功确认响应
     */
    public record CallbackSecurityResult(boolean signatureValid,
                                         boolean ipAllowed,
                                         RawChannelCallbackRequest request,
                                         VerifiedChannelEvent event,
                                         ChannelCallbackAcknowledgement acknowledgement) {
    }

}
