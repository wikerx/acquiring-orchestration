package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.RateLimitExceededException;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.web.trace.HttpTrafficLoggingFilter;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessDeniedException;
import com.scott.payment.openapi.security.MerchantKeyProvider;
import com.scott.payment.openapi.security.OpenApiMerchantRateLimitService;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestHeaderExtractor
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : Open API Request Header Extractor 提取组件，位于 商户开放接口服务，从请求、响应或配置中读取关键字段，完成标准化、校验和脱敏日志准备。
 * @status : create
 */
@Component
@Slf4j
public class OpenApiRequestHeaderExtractor {

    /**
     * 开放 API 授权请求头名称，商户 JWT 默认从该请求头读取。
     */
    private static final String HEADER_AUTHORIZATION = "authorization";

    /** 商户 OpenAPI 请求体媒体类型请求头。 */
    private static final String HEADER_CONTENT_TYPE = "content-type";

    /**
     * Authorization 请求头可选 Bearer 前缀，兼容标准网关和商户直连两种写法。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 商户 JWT 验签器，负责校验 Header、Payload、签名和有效期。
     */
    private final MerchantJwtVerifier merchantJwtVerifier;

    /**
     * 商户密钥提供器，根据 JWT 中的 merchantId 查询 merchantKey。
     */
    private final MerchantKeyProvider merchantKeyProvider;

    /**
     * JWT jti 防重放服务，Redis 可用时写入防重放键。
     */
    private final OpenApiJwtReplayProtectionService replayProtectionService;

    /**
     * 商户 IP 白名单访问控制服务，JWT 验签后、防重放写入前执行校验。
     */
    private final MerchantIpWhitelistAccessService ipWhitelistAccessService;

    /** 商户所有 OpenAPI 累计滑动窗口限流，白名单通过后、防重放写入前执行。 */
    private final OpenApiMerchantRateLimitService merchantRateLimitService;

    /**
     * 安全拦截事件记录器，仅记录脱敏排查元数据。
     */
    private final SecurityInterceptEventRecorder securityInterceptEventRecorder;

    /**
     * OpenAPI 诊断日志支撑组件，用于生成请求头和 JWT 的安全摘要。
     */
    private final OpenApiDiagnosticLogSupport diagnosticLogSupport;

    /**
     * 创建开放接口请求头提取器。
     *
     * @param merchantJwtVerifier    商户 JWT 验签器
     * @param merchantKeyProvider    商户密钥提供器
     * @param replayProtectionService JWT 防重放服务
     * @param ipWhitelistAccessService 商户 IP 白名单访问控制服务
     * @param merchantRateLimitService 商户维度滑动窗口限流服务
     * @param securityInterceptEventRecorder 安全拦截事件记录器
     */
    public OpenApiRequestHeaderExtractor(MerchantJwtVerifier merchantJwtVerifier,
                                         MerchantKeyProvider merchantKeyProvider,
                                         OpenApiJwtReplayProtectionService replayProtectionService,
                                         MerchantIpWhitelistAccessService ipWhitelistAccessService,
                                         OpenApiMerchantRateLimitService merchantRateLimitService,
                                         SecurityInterceptEventRecorder securityInterceptEventRecorder,
                                         OpenApiDiagnosticLogSupport diagnosticLogSupport) {
        this.merchantJwtVerifier = merchantJwtVerifier;
        this.merchantKeyProvider = merchantKeyProvider;
        this.replayProtectionService = replayProtectionService;
        this.ipWhitelistAccessService = ipWhitelistAccessService;
        this.merchantRateLimitService = merchantRateLimitService;
        this.securityInterceptEventRecorder = securityInterceptEventRecorder;
        this.diagnosticLogSupport = diagnosticLogSupport;
    }

    /**
     * 提取请求头并完成商户 JWT 验签。
     *
     * @param request         HTTP 请求
     * @param requiredHeaders 接口要求存在的请求头
     * @return 标准化请求头信息
     */
    public OpenApiRequestHeaderDTO extract(HttpServletRequest request, String[] requiredHeaders) {
        try {
            validateRequiredHeaders(request, requiredHeaders);
        } catch (RuntimeException exception) {
            recordBlocked(request, "OPENAPI_REQUIRED_HEADER_MISSING", SecurityInterceptEventRecorder.RISK_MEDIUM,
                    null, "OPENAPI_REQUIRED_HEADER", exception);
            throw exception;
        }
        String authorization = request.getHeader(HEADER_AUTHORIZATION);
        String token;
        try {
            token = resolveToken(authorization);
        } catch (RuntimeException exception) {
            recordBlocked(request, "OPENAPI_JWT_INVALID", SecurityInterceptEventRecorder.RISK_MEDIUM,
                    null, "OPENAPI_JWT", exception);
            throw exception;
        }
        String merchantId = null;
        JwtMerchantClaims claims;
        try {
            merchantId = merchantJwtVerifier.peekMerchantId(token);
            String merchantKey = merchantKeyProvider.getMerchantKey(merchantId);
            claims = merchantJwtVerifier.verify(token, merchantKey);
        } catch (RuntimeException exception) {
            recordBlocked(request, "OPENAPI_JWT_INVALID", SecurityInterceptEventRecorder.RISK_HIGH,
                    merchantId, "OPENAPI_JWT", exception);
            throw exception;
        }
        String clientIp;
        try {
            clientIp = ipWhitelistAccessService.checkAccess(claims.getMerchantId(), request);
        } catch (RuntimeException exception) {
            recordBlocked(request, "OPENAPI_IP_DENIED", SecurityInterceptEventRecorder.RISK_HIGH,
                    claims.getMerchantId(), ipWhitelistRuleCode(exception), exception);
            throw exception;
        }
        // 白名单已经确认客户端来源；限流拒绝发生在防重放写入前，避免消耗一次性 jti。
        try {
            merchantRateLimitService.check(claims.getMerchantId());
        } catch (RateLimitExceededException exception) {
            recordRateLimited(request, claims.getMerchantId(), exception);
            throw exception;
        }
        try {
            replayProtectionService.checkAndMark(claims.getMerchantId(), claims.getJwtId(), claims.getExpiresAt());
        } catch (RuntimeException exception) {
            recordBlocked(request, resolveReplayEventType(exception), SecurityInterceptEventRecorder.RISK_HIGH,
                    claims.getMerchantId(), "OPENAPI_JWT_REPLAY", exception);
            throw exception;
        }
        OpenApiRequestHeaderDTO headerDTO = new OpenApiRequestHeaderDTO();
        headerDTO.setAuthorization(token);
        headerDTO.setMerchantId(claims.getMerchantId());
        headerDTO.setJwtId(claims.getJwtId());
        headerDTO.setIssuedAt(claims.getIssuedAt());
        headerDTO.setExpiresAt(claims.getExpiresAt());
        headerDTO.setClientIp(clientIp);
        log.debug("event: OPENAPI_SECURITY_CHECK_END stage=AUTH traceId: {} merchantId: {} path: {} apiVersion: {} jwtValid=true jtiDigest: {} clientIpDigest: {} jwtSummary: {} headerSummary: {} httpRequestDigest: {} httpRequestLength: {}",
                TraceContext.getTraceId(),
                claims.getMerchantId(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                digest8(claims.getJwtId()),
                digest8(clientIp),
                diagnosticLogSupport.jwtSummary(headerDTO),
                diagnosticLogSupport.headerSummary(request),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_DIGEST_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_LENGTH_ATTRIBUTE));
        return headerDTO;
    }

    private String ipWhitelistRuleCode(RuntimeException exception) {
        if (exception instanceof MerchantIpWhitelistAccessDeniedException deniedException) {
            return "OPENAPI_IP_WHITELIST_" + deniedException.getReason().name();
        }
        return "OPENAPI_IP_WHITELIST_UNAVAILABLE";
    }

    /**
     * 记录 OpenAPI 安全拦截事件。
     * <p>
     * 该日志覆盖缺失头、JWT 验签失败、IP 白名单拒绝和防重放拒绝等认证阶段异常；
     * Authorization、JWT 原文和完整请求体只通过 header/body 摘要展示，不直接输出。
     * </p>
     *
     * @param request     当前 HTTP 请求
     * @param eventType   安全拦截事件类型
     * @param riskLevel   风险等级
     * @param merchantId  已解析出的商户号；认证前失败时允许为空
     * @param hitRuleCode 命中的安全规则编码
     * @param exception   触发拦截的业务异常
     */
    private void recordBlocked(HttpServletRequest request,
                               String eventType,
                               String riskLevel,
                               String merchantId,
                               String hitRuleCode,
                               RuntimeException exception) {
        if (request != null) {
            request.setAttribute(OpenApiRequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
        }
        securityInterceptEventRecorder.recordBlocked(
                request,
                SecurityInterceptEventRecorder.SOURCE_OPENAPI,
                eventType,
                riskLevel,
                merchantId,
                hitRuleCode,
                securityInterceptEventRecorder.reasonCode(exception),
                securityInterceptEventRecorder.reasonMessage(exception)
        );
        log.warn("event: OPENAPI_SECURITY_CHECK_END stage=AUTH traceId: {} merchantId: {} path: {} apiVersion: {} jwtValid=false ipAllowed=false requestIp: {} hitRuleCode: {} reasonCode: {} exceptionType: {} headerSummary: {} httpRequestDigest: {} httpRequestLength: {} httpRequestSummary: {}",
                TraceContext.getTraceId(),
                merchantId,
                request == null ? null : request.getRequestURI(),
                request == null ? null : request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                deniedRequestIp(exception),
                hitRuleCode,
                securityInterceptEventRecorder.reasonCode(exception),
                exception.getClass().getSimpleName(),
                diagnosticLogSupport.headerSummary(request),
                request == null ? null : request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_DIGEST_ATTRIBUTE),
                request == null ? null : request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_LENGTH_ATTRIBUTE),
                request == null ? null : request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_SUMMARY_ATTRIBUTE));
    }

    private String deniedRequestIp(RuntimeException exception) {
        if (exception instanceof MerchantIpWhitelistAccessDeniedException deniedException) {
            return StringUtils.hasText(deniedException.getRequestIp())
                    ? deniedException.getRequestIp()
                    : "unknown";
        }
        return null;
    }

    /**
     * 记录商户滑动窗口拒绝事件；只保存商户号、路径和规则编码，不记录 JWT、原始 IP 或请求体。
     */
    private void recordRateLimited(HttpServletRequest request,
                                   String merchantId,
                                   RateLimitExceededException exception) {
        request.setAttribute(OpenApiRequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
        securityInterceptEventRecorder.recordBlocked(
                request,
                SecurityInterceptEventRecorder.SOURCE_OPENAPI,
                "OPENAPI_MERCHANT_RATE_LIMITED",
                SecurityInterceptEventRecorder.RISK_MEDIUM,
                merchantId,
                "OPENAPI_MERCHANT_RATE_LIMIT",
                securityInterceptEventRecorder.reasonCode(exception),
                securityInterceptEventRecorder.reasonMessage(exception)
        );
        log.warn("event: OPENAPI_MERCHANT_RATE_LIMITED traceId: {} merchantId: {} path: {} retryAfterSeconds: {}",
                TraceContext.getTraceId(), merchantId, request.getRequestURI(),
                exception.getRetryAfterSeconds());
    }

    /**
     * 生成开放接口幂等标识的短摘要，用于日志关联 JWT jti、防重放记录和安全拦截记录。
     * <p>
     * 输入值可能来自商户 JWT 声明；只输出 SHA-256 前 16 位十六进制摘要，不记录原始 jti。
     * 该方法不修改请求状态，不访问外部系统，摘要仅用于排查同一次开放接口认证链路。
     * </p>
     * @param value 商户 JWT jti 或其他待摘要文本，允许为空
     * @return 摘要文本；入参为空时返回 null；本地算法不可用时返回固定降级标识
     */
    private String digest8(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return "sha256_unavailable";
        }
    }

    private String resolveReplayEventType(RuntimeException exception) {
        String reasonCode = securityInterceptEventRecorder.reasonCode(exception);
        if (ApiResultEnum.INTERNAL_SERVER_ERROR.getCode().equals(reasonCode)) {
            return "OPENAPI_REPLAY_UNAVAILABLE";
        }
        return "OPENAPI_REPLAY_DENIED";
    }

    /**
     * 校验当前接口声明必须携带的请求头。
     * <p>
     * Authorization 会返回稳定的未授权错误码，其他缺失请求头会返回必填参数缺失错误码，便于商户排查接入问题。
     *
     * @param request         HTTP 请求
     * @param requiredHeaders 当前接口要求存在的请求头名称列表
     */
    private void validateRequiredHeaders(HttpServletRequest request, String[] requiredHeaders) {
        if (requiredHeaders == null || requiredHeaders.length == 0) {
            return;
        }
        for (String header : requiredHeaders) {
            String headerValue = request.getHeader(header);
            if (!StringUtils.hasText(headerValue)) {
                if (HEADER_AUTHORIZATION.equalsIgnoreCase(header)) {
                    throw new ApiException(ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
                }
                throw new ApiException(ApiResultEnum.PARAM_MISSING, "header." + header);
            }
            if (HEADER_CONTENT_TYPE.equalsIgnoreCase(header) && !isJsonContentType(headerValue)) {
                throw new ApiException(ApiResultEnum.PARAM_INVALID, "header." + header);
            }
        }
    }

    /**
     * 判断 Content-Type 是否为 JSON 媒体类型，兼容 charset 等合法参数。
     *
     * @param contentType 请求头原始值
     * @return application/json 兼容类型返回 true
     */
    private boolean isJsonContentType(String contentType) {
        try {
            return MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 从 Authorization 请求头解析 JWT Token。
     * <p>
     * 支持标准 Bearer Token 和直接传 JWT 两种形式，降低商户接入门槛。
     *
     * @param authorization 原始 Authorization 请求头
     * @return 去除 Bearer 前缀后的 JWT Token
     */
    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
        }
        if (authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return authorization.trim();
    }
}
