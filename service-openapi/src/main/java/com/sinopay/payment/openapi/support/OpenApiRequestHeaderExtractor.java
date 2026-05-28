package com.sinopay.payment.openapi.support;

import com.sinopay.payment.component.core.constant.ErrorCode;
import com.sinopay.payment.component.core.exception.BizException;
import com.sinopay.payment.component.security.jwt.JwtMerchantClaims;
import com.sinopay.payment.component.security.jwt.MerchantJwtVerifier;
import com.sinopay.payment.openapi.api.rest.v1.dto.header.OpenApiRequestHeaderDTO;
import com.sinopay.payment.openapi.security.MerchantKeyProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

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

    private static final String HEADER_AUTHORIZATION = "authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final MerchantJwtVerifier merchantJwtVerifier;
    private final MerchantKeyProvider merchantKeyProvider;

    public OpenApiRequestHeaderExtractor(MerchantJwtVerifier merchantJwtVerifier,
                                         MerchantKeyProvider merchantKeyProvider) {
        this.merchantJwtVerifier = merchantJwtVerifier;
        this.merchantKeyProvider = merchantKeyProvider;
    }

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
                throw new BizException(ErrorCode.PARAM_INVALID, "missing required header: " + header);
            }
        }
    }

    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new BizException(ErrorCode.SIGN_INVALID, "authorization jwt is required");
        }
        if (authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return authorization.trim();
    }
}
