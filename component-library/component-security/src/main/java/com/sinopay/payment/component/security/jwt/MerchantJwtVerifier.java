package com.sinopay.payment.component.security.jwt;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sinopay.payment.component.core.constant.ErrorCode;
import com.sinopay.payment.component.core.exception.BizException;
import com.sinopay.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String JWT_TYPE = "JWT";
    private static final String JWT_ALGORITHM = "HS256";
    private static final String EXPECTED_AUDIENCE = "gateway";
    private static final String EXPECTED_ISSUER = "merchant";
    private static final long MAX_TOKEN_SECONDS = 180L;
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 60L;

    public String peekMerchantId(String token) {
        JSONObject payload = parsePayload(splitToken(token));
        String merchantId = payload.getString("merchantId");
        if (!StringUtils.hasText(merchantId)) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt merchantId is required");
        }
        return merchantId;
    }

    public JwtMerchantClaims verify(String token, String merchantKey) {
        return verify(token, merchantKey, System.currentTimeMillis() / 1000L);
    }

    public JwtMerchantClaims verify(String token, String merchantKey, long nowEpochSeconds) {
        if (!StringUtils.hasText(merchantKey)) {
            throw new BizException(ErrorCode.SIGN_INVALID, "merchant key is required");
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
            throw new BizException(ErrorCode.SIGN_INVALID, "authorization jwt is required");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BizException(ErrorCode.SIGN_INVALID, "authorization jwt format invalid");
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
            throw new BizException(ErrorCode.SIGN_INVALID, "authorization jwt can not be decoded");
        }
    }

    private void validateHeader(JSONObject header) {
        if (!JWT_TYPE.equalsIgnoreCase(header.getString("typ"))) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt typ must be JWT");
        }
        if (!JWT_ALGORITHM.equalsIgnoreCase(header.getString("alg"))) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt alg must be HS256");
        }
    }

    private void validateSignature(String[] parts, String merchantKey) {
        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = base64UrlEncode(hmacSha256(signingInput, merchantKey));
        boolean matched = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII)
        );
        if (!matched) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt signature invalid");
        }
    }

    private JwtMerchantClaims validatePayload(JSONObject payload, long nowEpochSeconds) {
        validateAudience(payload.getJSONArray("aud"));
        if (!EXPECTED_ISSUER.equals(payload.getString("iss"))) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt iss invalid");
        }
        String jwtId = payload.getString("jti");
        String merchantId = payload.getString("merchantId");
        if (!StringUtils.hasText(jwtId)) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt jti is required");
        }
        if (!StringUtils.hasText(merchantId)) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt merchantId is required");
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
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt aud invalid");
        }
    }

    private void validateTimeWindow(long issuedAt, long expiresAt, long nowEpochSeconds) {
        if (issuedAt <= 0L || expiresAt <= 0L || expiresAt <= issuedAt) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt iat or exp invalid");
        }
        if (expiresAt - issuedAt > MAX_TOKEN_SECONDS) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt exp can not exceed iat by 3 minutes");
        }
        if (issuedAt > nowEpochSeconds + ALLOWED_CLOCK_SKEW_SECONDS) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt iat is in the future");
        }
        if (expiresAt <= nowEpochSeconds) {
            throw new BizException(ErrorCode.SIGN_INVALID, "jwt expired");
        }
    }

    private byte[] hmacSha256(String signingInput, String merchantKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(merchantKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "jwt signature can not be calculated");
        }
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(padBase64Url(value));
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String padBase64Url(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }
}
