package com.sinopay.payment.component.security.jwt;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sinopay.payment.component.core.enums.ApiCoResultEnum;
import com.sinopay.payment.component.core.exception.ApiException;
import com.sinopay.payment.component.core.json.JsonUtils;
import com.sinopay.payment.component.security.crypto.HmacSha256Signer;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

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

    private final HmacSha256Signer signer = new HmacSha256Signer();

    /**
     * 仅解析 JWT Payload 中的 merchantId，用于查询商户密钥。
     *
     * @param token 商户 JWT
     * @return 商户号
     */
    public String peekMerchantId(String token) {
        JSONObject payload = parsePayload(splitToken(token));
        String merchantId = payload.getString("merchantId");
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
        String[] parts = splitToken(token);
        JSONObject header = parseHeader(parts);
        JSONObject payload = parsePayload(parts);
        validateHeader(header);
        validateSignature(parts, merchantKey);
        return validatePayload(payload, nowEpochSeconds);
    }

    private String[] splitToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_NULL);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
        return parts;
    }

    private JSONObject parseHeader(String[] parts) {
        return parseJsonPart(parts[0]);
    }

    private JSONObject parsePayload(String[] parts) {
        return parseJsonPart(parts[1]);
    }

    private JSONObject parseJsonPart(String value) {
        try {
            String json = new String(base64UrlDecode(value), StandardCharsets.UTF_8);
            return JsonUtils.parseObject(json, JSONObject.class);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
    }

    private void validateHeader(JSONObject header) {
        if (!JWT_TYPE.equalsIgnoreCase(header.getString("typ"))) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
        if (!JWT_ALGORITHM.equalsIgnoreCase(header.getString("alg"))) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
    }

    private void validateSignature(String[] parts, String merchantKey) {
        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = signer.signBase64Url(signingInput, merchantKey);
        boolean matched = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII)
        );
        if (!matched) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_SIGN);
        }
    }

    private JwtMerchantClaims validatePayload(JSONObject payload, long nowEpochSeconds) {
        validateAudience(payload.getJSONArray("aud"));
        if (!EXPECTED_ISSUER.equals(payload.getString("iss"))) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_ISS);
        }
        String jwtId = payload.getString("jti");
        String merchantId = payload.getString("merchantId");
        if (!StringUtils.hasText(jwtId)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_MER_INVALID);
        }
        long issuedAt = payload.getLongValue("iat");
        long expiresAt = payload.getLongValue("exp");
        validateTimeWindow(issuedAt, expiresAt, nowEpochSeconds);

        JwtMerchantClaims claims = new JwtMerchantClaims();
        claims.setMerchantId(merchantId);
        claims.setJwtId(jwtId);
        claims.setIssuedAt(issuedAt);
        claims.setExpiresAt(expiresAt);
        return claims;
    }

    private void validateAudience(JSONArray audiences) {
        if (audiences == null || !audiences.contains(EXPECTED_AUDIENCE)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_AUD);
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

    private byte[] base64UrlDecode(String value) {
        try {
            return Base64.getUrlDecoder().decode(padBase64Url(value));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
    }

    private String padBase64Url(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }
}
