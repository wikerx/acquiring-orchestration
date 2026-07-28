package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
     * Worldpay 事件签名请求头，格式通常为 keyId/SHA256/signature。
     */
    public static final String WORLDPAY_EVENT_SIGNATURE_HEADER = "Event-Signature";

    /**
     * 商户通知重试维护密钥请求头。
     */
    public static final String NOTIFY_RETRY_TOKEN_HEADER = "X-Notify-Retry-Token";

    /**
     * 渠道回调签名算法常量，签名原文和密钥不得写入日志。
     */
    private static final String HMAC_SHA256 = "HmacSHA256";

    private static final Set<String> WORLDPAY_CHANNELS = Set.of("WPGJSON", "WPGXML");

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
        boolean ipAllowed = verifyChannelIp(channelCode, request);
        if (!callbackProperties.isChannelSignatureRequired()) {
            return new CallbackSecurityResult(true, ipAllowed);
        }
        String worldpayEventSignature = request.getHeader(WORLDPAY_EVENT_SIGNATURE_HEADER);
        if (isWorldpay(channelCode) && StringUtils.hasText(worldpayEventSignature)) {
            verifyWorldpayEventSignature(channelCode, request, rawBody, worldpayEventSignature);
            return new CallbackSecurityResult(true, ipAllowed);
        }
        verifyInternalChannelSignature(channelCode, request, rawBody);
        return new CallbackSecurityResult(true, ipAllowed);
    }

    /**
     * 校验平台自定义渠道回调签名。
     * <p>
     * 该签名用于未提供官方 webhook 签名格式的渠道，签名文本绑定 method、path、时间戳、nonce、渠道编码和原文 body 摘要。
     *
     * @param channelCode 渠道编码
     * @param request HTTP 请求
     * @param rawBody 回调原文
     */
    private void verifyInternalChannelSignature(String channelCode, HttpServletRequest request, String rawBody) {
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
        String normalizedChannelCode = normalizeConfigKey(channelCode);
        String channelSecret = channelSecret(channelCode);
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
    }

    /**
     * 校验 Worldpay Event-Signature。
     * <p>
     * Worldpay 回调签名头携带 keyId、算法和签名值，平台使用对应 keyId 的共享密钥对原始 body 计算 HMAC-SHA256。
     *
     * @param channelCode 渠道编码
     * @param request HTTP 请求
     * @param rawBody 回调原文
     * @param eventSignature Event-Signature 请求头
     */
    private void verifyWorldpayEventSignature(String channelCode,
                                              HttpServletRequest request,
                                              String rawBody,
                                              String eventSignature) {
        WorldpayEventSignature parsedSignature = parseWorldpayEventSignature(request, eventSignature);
        String secret = worldpayEventSecret(channelCode, parsedSignature.keyId());
        if (!StringUtils.hasText(secret)) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_CALLBACK_SECRET_MISSING",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "WORLDPAY_EVENT_SIGNATURE",
                    "worldpay callback event signature secret is not configured");
        }
        if (!"SHA256".equalsIgnoreCase(parsedSignature.algorithm())) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_ALGORITHM_UNSUPPORTED",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "WORLDPAY_EVENT_SIGNATURE",
                    "worldpay callback event signature algorithm is unsupported");
        }
        String expectedSignature = hmacSha256(rawBody == null ? "" : rawBody, secret);
        if (!InternalServiceSignature.matches(expectedSignature, parsedSignature.signature())) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_INVALID",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "WORLDPAY_EVENT_SIGNATURE",
                    "worldpay callback event signature is invalid");
        }
    }

    /**
     * 解析 Worldpay Event-Signature 请求头。
     *
     * @param eventSignature 原始签名头
     * @return 解析后的 keyId、算法和签名值
     */
    private WorldpayEventSignature parseWorldpayEventSignature(HttpServletRequest request, String eventSignature) {
        String[] segments = eventSignature == null ? new String[0] : eventSignature.split("/", 3);
        if (segments.length != 3
                || !StringUtils.hasText(segments[0])
                || !StringUtils.hasText(segments[1])
                || !StringUtils.hasText(segments[2])) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_HEADER_INVALID",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "WORLDPAY_EVENT_SIGNATURE",
                    "worldpay callback event signature is invalid");
        }
        return new WorldpayEventSignature(segments[0].trim(), segments[1].trim(), segments[2].trim());
    }

    /**
     * 读取 Worldpay 指定 keyId 对应的事件签名密钥。
     *
     * @param channelCode 渠道编码
     * @param keyId Worldpay 签名 keyId
     * @return HMAC-SHA256 共享密钥
     */
    private String worldpayEventSecret(String channelCode, String keyId) {
        Map<String, String> secrets = channelEventSecrets(channelCode);
        String secret = secrets.get(keyId);
        if (!StringUtils.hasText(secret)) {
            secret = secrets.get(normalizeConfigKey(keyId));
        }
        if (StringUtils.hasText(secret)) {
            return secret;
        }
        return channelSecret(channelCode);
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
     * 判断是否为 Worldpay 独立渠道。
     *
     * @param channelCode 渠道编码
     * @return true 表示 WPGJSON 或 WPGXML
     */
    private boolean isWorldpay(String channelCode) {
        return WORLDPAY_CHANNELS.contains(normalizeChannelCode(channelCode));
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
     * 计算 HMAC-SHA256 十六进制签名。
     *
     * @param rawBody 回调原文
     * @param secret 共享密钥
     * @return 小写十六进制签名
     */
    private String hmacSha256(String rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return java.util.HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR, "channel callback signature can not be calculated");
        }
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
     * 解析parsetimestamp，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户开放接口服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param timestampText 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 构造、转换或解析后的业务值
     */
    private long parseTimestamp(String timestampText) {
        try {
            return Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature timestamp is invalid");
        }
    }

    /**
     * 计算SHA-256 十六进制摘要，用不可逆指纹关联原始内容而不暴露明文。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rawBody raw Body 输入值，参与 raw报文体 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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

    /**
     * Worldpay Event-Signature 解析结果。
     *
     * @param keyId 签名密钥标识
     * @param algorithm 签名算法
     * @param signature 请求头中的签名值
     */
    private record WorldpayEventSignature(String keyId, String algorithm, String signature) {
    }
}
