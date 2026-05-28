package com.scott.payment.component.security.jwt;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantJwtVerifier
 * @date : 2026-05-28 11:42
 * @email : scott_x@163.com
 * @description : 商户 JWT HS256 验签器
 * @status : create
 */
public class MerchantJwtVerifier {

    private static final String JWT_TYPE = "JWT";
    private static final String JWT_ALGORITHM = "HS256";
    private static final String EXPECTED_AUDIENCE = "gateway";
    private static final String EXPECTED_ISSUER = "merchant";
    private static final long MAX_TOKEN_SECONDS = 180L;
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 60L;

    /**
     * 仅解析 JWT Payload 中的 merchantId，用于查询商户密钥。
     *
     * @param token 商户 JWT
     * @return 商户号
     */
    public String peekMerchantId(String token) {
        JWT jwt = parseToken(token);
        String merchantId = asString(jwt.getPayload("merchantId"));
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_MER_INVALID);
        }
        return merchantId;
    }

    /**
     * 使用当前系统时间验证商户 JWT。
     *
     * @param token       商户 JWT
     * @param merchantKey 商户签名密钥
     * @return JWT 声明
     */
    public JwtMerchantClaims verify(String token, String merchantKey) {
        return verify(token, merchantKey, System.currentTimeMillis() / 1000L);
    }

    /**
     * 使用指定时间验证商户 JWT，便于单元测试和时间漂移控制。
     *
     * @param token           商户 JWT
     * @param merchantKey     商户签名密钥
     * @param nowEpochSeconds 当前秒级时间戳
     * @return JWT 声明
     */
    public JwtMerchantClaims verify(String token, String merchantKey, long nowEpochSeconds) {
        if (!StringUtils.hasText(merchantKey)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_NO_KEY);
        }
        JWT jwt = parseToken(token);
        validateHeader(jwt);
        validateSignature(jwt, merchantKey);
        return validatePayload(jwt, nowEpochSeconds);
    }

    private JWT parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_NULL);
        }
        try {
            return JWTUtil.parseToken(token);
        } catch (Exception exception) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
    }

    private void validateHeader(JWT jwt) {
        if (!JWT_TYPE.equalsIgnoreCase(asString(jwt.getHeader(JWTHeader.TYPE)))) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
        if (!JWT_ALGORITHM.equalsIgnoreCase(asString(jwt.getHeader(JWTHeader.ALGORITHM)))) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
    }

    private void validateSignature(JWT jwt, String merchantKey) {
        JWTSigner signer = JWTSignerUtil.hs256(merchantKey.getBytes(StandardCharsets.UTF_8));
        if (!jwt.verify(signer)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_SIGN);
        }
    }

    private JwtMerchantClaims validatePayload(JWT jwt, long nowEpochSeconds) {
        validateAudience(jwt.getPayload(RegisteredPayload.AUDIENCE));
        if (!EXPECTED_ISSUER.equals(asString(jwt.getPayload(RegisteredPayload.ISSUER)))) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_ISS);
        }
        String jwtId = asString(jwt.getPayload(RegisteredPayload.JWT_ID));
        String merchantId = asString(jwt.getPayload("merchantId"));
        if (!StringUtils.hasText(jwtId)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_MER_INVALID);
        }
        long issuedAt = toEpochSeconds(jwt.getPayload(RegisteredPayload.ISSUED_AT), ApiCoResultEnum.CO_UNAUTHORIZED_JWT_IAT);
        long expiresAt = toEpochSeconds(jwt.getPayload(RegisteredPayload.EXPIRES_AT), ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP);
        validateTimeWindow(issuedAt, expiresAt, nowEpochSeconds);

        JwtMerchantClaims claims = new JwtMerchantClaims();
        claims.setMerchantId(merchantId);
        claims.setJwtId(jwtId);
        claims.setIssuedAt(issuedAt);
        claims.setExpiresAt(expiresAt);
        return claims;
    }

    private void validateAudience(Object audiences) {
        if (audiences instanceof Collection) {
            if (((Collection<?>) audiences).contains(EXPECTED_AUDIENCE)) {
                return;
            }
        }
        if (EXPECTED_AUDIENCE.equals(asString(audiences))) {
            return;
        }
        throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_AUD);
    }

    private long toEpochSeconds(Object value, ApiCoResultEnum errorCode) {
        if (value instanceof Date) {
            return ((Date) value).getTime() / 1000L;
        }
        if (value instanceof Number) {
            long timestamp = ((Number) value).longValue();
            return timestamp > 10_000_000_000L ? timestamp / 1000L : timestamp;
        }
        String text = asString(value);
        if (!StringUtils.hasText(text)) {
            throw new ApiException(errorCode);
        }
        try {
            long timestamp = Long.parseLong(text);
            return timestamp > 10_000_000_000L ? timestamp / 1000L : timestamp;
        } catch (NumberFormatException exception) {
            throw new ApiException(errorCode);
        }
    }

    private void validateTimeWindow(long issuedAt, long expiresAt, long nowEpochSeconds) {
        if (issuedAt <= 0L || expiresAt <= 0L || expiresAt <= issuedAt) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP);
        }
        if (expiresAt - issuedAt > MAX_TOKEN_SECONDS) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP);
        }
        if (issuedAt > nowEpochSeconds + ALLOWED_CLOCK_SKEW_SECONDS) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_IAT);
        }
        if (expiresAt <= nowEpochSeconds) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
