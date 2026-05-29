package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
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

    public OpenApiRequestHeaderExtractor(MerchantJwtVerifier merchantJwtVerifier,
                                         MerchantKeyProvider merchantKeyProvider) {
        this.merchantJwtVerifier = merchantJwtVerifier;
        this.merchantKeyProvider = merchantKeyProvider;
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

        OpenApiRequestHeaderDTO headerDTO = new OpenApiRequestHeaderDTO();
        headerDTO.setAuthorization(token);
        headerDTO.setMerchantId(claims.getMerchantId());
        headerDTO.setJwtId(claims.getJwtId());
        headerDTO.setIssuedAt(claims.getIssuedAt());
        headerDTO.setExpiresAt(claims.getExpiresAt());
        return headerDTO;
    }

    private void validateRequiredHeaders(HttpServletRequest request, String[] requiredHeaders) {
        if (requiredHeaders == null || requiredHeaders.length == 0) {
            return;
        }
        for (String header : requiredHeaders) {
            if (!StringUtils.hasText(request.getHeader(header))) {
                if (HEADER_AUTHORIZATION.equalsIgnoreCase(header)) {
                    throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_NULL);
                }
                throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING, "header." + header);
            }
        }
    }

    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_NULL);
        }
        if (authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return authorization.trim();
    }
}
