package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.MerchantKeyProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestHeaderExtractor
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口请求头提取与格式校验器
 * @status : create
 */
@Component
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

    public OpenApiRequestHeaderExtractor(MerchantJwtVerifier merchantJwtVerifier,
                                         MerchantKeyProvider merchantKeyProvider,
                                         OpenApiJwtReplayProtectionService replayProtectionService) {
        this.merchantJwtVerifier = merchantJwtVerifier;
        this.merchantKeyProvider = merchantKeyProvider;
        this.replayProtectionService = replayProtectionService;
    }

    /**
     * 提取请求头并完成商户 JWT 验签。
     *
     * @param request         HTTP 请求
     * @param requiredHeaders 接口要求存在的请求头
     * @return 标准化请求头信息
     */
    public OpenApiRequestHeaderDTO extract(HttpServletRequest request, String[] requiredHeaders) {
        validateRequiredHeaders(request, requiredHeaders);
        String authorization = request.getHeader(HEADER_AUTHORIZATION);
        String token = resolveToken(authorization);
        String merchantId = merchantJwtVerifier.peekMerchantId(token);
        String merchantKey = merchantKeyProvider.getMerchantKey(merchantId);
        JwtMerchantClaims claims = merchantJwtVerifier.verify(token, merchantKey);
        replayProtectionService.checkAndMark(claims.getMerchantId(), claims.getJwtId(), claims.getExpiresAt());

        OpenApiRequestHeaderDTO headerDTO = new OpenApiRequestHeaderDTO();
        headerDTO.setAuthorization(token);
        headerDTO.setMerchantId(claims.getMerchantId());
        headerDTO.setJwtId(claims.getJwtId());
        headerDTO.setIssuedAt(claims.getIssuedAt());
        headerDTO.setExpiresAt(claims.getExpiresAt());
        return headerDTO;
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
