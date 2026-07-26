package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.openapi.security.MerchantKeyProvider;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


@Component
@Slf4j
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestHeaderExtractor
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : OpenApiRequestHeaderExtractor Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class OpenApiRequestHeaderExtractor {

    /**
     * 开放 API 授权请求头名称，商户 JWT 默认从该请求头读取。
     */
    private static final String HEADER_AUTHORIZATION = "authorization";

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
     * @param securityInterceptEventRecorder 安全拦截事件记录器
     */
    public OpenApiRequestHeaderExtractor(MerchantJwtVerifier merchantJwtVerifier,
                                         MerchantKeyProvider merchantKeyProvider,
                                         OpenApiJwtReplayProtectionService replayProtectionService,
                                         MerchantIpWhitelistAccessService ipWhitelistAccessService,
                                         SecurityInterceptEventRecorder securityInterceptEventRecorder,
                                         OpenApiDiagnosticLogSupport diagnosticLogSupport) {
        this.merchantJwtVerifier = merchantJwtVerifier;
        this.merchantKeyProvider = merchantKeyProvider;
        this.replayProtectionService = replayProtectionService;
        this.ipWhitelistAccessService = ipWhitelistAccessService;
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
                    claims.getMerchantId(), "OPENAPI_IP_WHITELIST", exception);
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
        log.info("event: OPENAPI_SECURITY_CHECK_END stage=AUTH merchantId: {} path: {} apiVersion: {} jwtValid=true jtiDigest: {} ipAllowed=true clientIp: {} jwtSummary: {} headerSummary: {}",
                claims.getMerchantId(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                digest8(claims.getJwtId()),
                clientIp,
                diagnosticLogSupport.jwtSummary(headerDTO),
                diagnosticLogSupport.headerSummary(request));
        return headerDTO;
    }

/**
 * 写入或更新 record Blocked 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 OpenApiRequestHeaderExtractor 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
 * @param eventType event Type 输入值，含义由调用方法名称和所属业务对象限定
 * @param riskLevel risk Level 输入值，含义由调用方法名称和所属业务对象限定
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param hitRuleCode hit Rule Code 输入值，含义由调用方法名称和所属业务对象限定
 * @param exception exception 输入值，含义由调用方法名称和所属业务对象限定
 */
    private void recordBlocked(HttpServletRequest request,
                               String eventType,
                               String riskLevel,
                               String merchantId,
                               String hitRuleCode,
                               RuntimeException exception) {
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
        log.warn("event: OPENAPI_SECURITY_CHECK_END stage=AUTH merchantId: {} path: {} apiVersion: {} jwtValid=false ipAllowed=false hitRuleCode: {} reasonCode: {} headerSummary: {}",
                merchantId,
                request == null ? null : request.getRequestURI(),
                request == null ? null : request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                hitRuleCode,
                securityInterceptEventRecorder.reasonCode(exception),
                diagnosticLogSupport.headerSummary(request));
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

    /**
     * 解析开放接口调用方 IP，用于认证日志、IP 白名单判断记录和安全拦截事件。
     * <p>
     * 优先使用网关透传的 X-Forwarded-For 首个地址，缺失时回退到 Servlet 远端地址。
     * 返回值只作为访问来源摘要，不承载 IP 库明细，不写入商户请求密文或 JWT 内容。
     * </p>
     * @param request 当前开放接口 HTTP 请求，不允许为空
     * @return 调用方 IP 文本
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 解析 resolve Replay Event Type 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 OpenApiRequestHeaderExtractor 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param exception exception 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
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
            if (!StringUtils.hasText(request.getHeader(header))) {
                if (HEADER_AUTHORIZATION.equalsIgnoreCase(header)) {
                    throw new ApiException(ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
                }
                throw new ApiException(ApiResultEnum.PARAM_MISSING, "header." + header);
            }
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
