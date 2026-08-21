package com.scott.payment.openapi.support;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import com.scott.payment.channel.payment.registry.PaymentChannelCallbackVerifierRegistry;
import com.scott.payment.channel.payment.security.HmacPaymentChannelCallbackVerifier;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final int MAX_VERIFICATION_HEADER_COUNT = 32;
    private static final int MAX_VERIFICATION_HEADER_NAME_LENGTH = 128;
    private static final int MAX_VERIFICATION_HEADER_VALUE_LENGTH = 4096;

    /**
     * 渠道回调时间戳请求头。
     */
    public static final String CHANNEL_TIMESTAMP_HEADER = HmacPaymentChannelCallbackVerifier.TIMESTAMP_HEADER;

    /**
     * 渠道回调随机串请求头。
     */
    public static final String CHANNEL_NONCE_HEADER = HmacPaymentChannelCallbackVerifier.NONCE_HEADER;

    /**
     * 渠道回调签名请求头。
     */
    public static final String CHANNEL_SIGNATURE_HEADER = HmacPaymentChannelCallbackVerifier.SIGNATURE_HEADER;

    /**
     * 渠道事件签名请求头，格式为 keyId/SHA256/signature。
     */
    public static final String CHANNEL_EVENT_SIGNATURE_HEADER = HmacPaymentChannelCallbackVerifier.EVENT_SIGNATURE_HEADER;

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

    /** 按渠道编码定位 provider 验签实现。 */
    private final PaymentChannelCallbackVerifierRegistry callbackVerifierRegistry;

    /**
     * 创建回调入口安全校验组件。
     *
     * @param callbackProperties 回调安全配置
     * @param securityInterceptEventRecorder 安全拦截事件记录器
     */
    @Autowired
    public OpenApiCallbackSecuritySupport(OpenApiCallbackProperties callbackProperties,
                                          SecurityInterceptEventRecorder securityInterceptEventRecorder,
                                          PaymentChannelCallbackVerifierRegistry callbackVerifierRegistry) {
        this.callbackProperties = callbackProperties;
        this.securityInterceptEventRecorder = securityInterceptEventRecorder;
        this.callbackVerifierRegistry = callbackVerifierRegistry;
    }

    /** 兼容不启动 Spring 容器的安全单元测试，生产环境使用三参数构造器注入 Registry。 */
    public OpenApiCallbackSecuritySupport(OpenApiCallbackProperties callbackProperties,
                                          SecurityInterceptEventRecorder securityInterceptEventRecorder) {
        this(callbackProperties, securityInterceptEventRecorder,
                new PaymentChannelCallbackVerifierRegistry(List.of(new HmacPaymentChannelCallbackVerifier())));
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
        boolean ipAllowed = verifyChannelIp(channelCode, request);
        if (!callbackProperties.isChannelSignatureRequired()) {
            return new CallbackSecurityResult(true, ipAllowed);
        }
        Map<String, String> headers = verificationHeaders(request);
        try {
            callbackVerifierRegistry.verify(new ChannelCallbackVerificationRequest(
                    channelCode,
                    request.getMethod(),
                    request.getRequestURI(),
                    headers,
                    rawBody,
                    channelSecret(channelCode),
                    channelEventSecrets(channelCode),
                    callbackProperties.getAllowedClockSkewMillis(),
                    InternalServiceSignature.currentTimeMillis()));
        } catch (ChannelCallbackVerificationException exception) {
            throw mapVerificationException(request, exception,
                    StringUtils.hasText(request.getHeader(CHANNEL_EVENT_SIGNATURE_HEADER)));
        } catch (com.scott.payment.channel.payment.exception.ChannelException exception) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_CALLBACK_VERIFIER_MISSING",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback verifier is not configured");
        }
        return new CallbackSecurityResult(true, ipAllowed);
    }

    /**
     * Collect a bounded HTTP header context for provider-specific callback verification.
     * Header values are never logged here; persistence uses the controller's masked summary.
     */
    private Map<String, String> verificationHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        while (names.hasMoreElements() && headers.size() < MAX_VERIFICATION_HEADER_COUNT) {
            String name = names.nextElement();
            if (!StringUtils.hasText(name) || name.length() > MAX_VERIFICATION_HEADER_NAME_LENGTH) {
                continue;
            }
            String value = request.getHeader(name);
            if (value != null) {
                headers.put(name, value.length() <= MAX_VERIFICATION_HEADER_VALUE_LENGTH
                        ? value : value.substring(0, MAX_VERIFICATION_HEADER_VALUE_LENGTH));
            }
        }
        return headers;
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
        ApiResultEnum result = exception.getReason() == ChannelCallbackVerificationException.Reason.INTERNAL_ERROR
                ? ApiResultEnum.INTERNAL_SERVER_ERROR
                : ApiResultEnum.UNAUTHORIZED;
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
    private String resolveClientIp(HttpServletRequest request) {
        String gatewayClientIp = request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP);
        if (StringUtils.hasText(gatewayClientIp)) {
            return gatewayClientIp.trim();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
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
 * 记录andreturnopenapi异常，写入安全、审计或链路排障所需的脱敏上下文。
 * <p>
 * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
 * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param eventType event Type 输入值，参与 eventtype 的查询、校验、转换、写入或日志摘要
 * @param riskLevel risk Level 输入值，参与 风控level 的查询、校验、转换、写入或日志摘要
 * @param hitRuleCode hit Rule Code 输入值，参与 hit规则编码 的查询、校验、转换、写入或日志摘要
 * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
 * 记录andreturn渠道异常，写入安全、审计或链路排障所需的脱敏上下文。
 * <p>
 * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
 * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param eventType event Type 输入值，参与 eventtype 的查询、校验、转换、写入或日志摘要
 * @param riskLevel risk Level 输入值，参与 风控level 的查询、校验、转换、写入或日志摘要
 * @param hitRuleCode hit Rule Code 输入值，参与 hit规则编码 的查询、校验、转换、写入或日志摘要
 * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private ApiException recordAndReturnChannelException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message) {
        return recordAndReturnChannelException(request, eventType, riskLevel, hitRuleCode,
                message, ApiResultEnum.UNAUTHORIZED);
    }

    private ApiException recordAndReturnChannelException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message,
                                                         ApiResultEnum result) {
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
     * @param ipAllowed IP 白名单是否通过；当前配置尚未启用独立 IP 规则时按通过记录
     */
    public record CallbackSecurityResult(boolean signatureValid, boolean ipAllowed) {
    }

}
