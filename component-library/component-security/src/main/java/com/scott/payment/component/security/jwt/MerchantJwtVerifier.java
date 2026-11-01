package com.scott.payment.component.security.jwt;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;

/**
 * 商户 JWT HS256 验签器，负责校验开放接口 token 的头部、签名、aud/iss、有效期和 merchantId 声明。
 */
public class MerchantJwtVerifier {

    /**
     * JWT Header 中约定的 token 类型，商户侧必须生成标准 JWT。
     */
    private static final String JWT_TYPE = "JWT";

    /**
     * 开放 API 当前支持的 JWT 签名算法，固定为 HS256/HmacSHA256。
     */
    private static final String JWT_ALGORITHM = "HS256";

    /**
     * JWT Payload 中 aud 的固定接收方，避免商户将其他系统 token 误用于网关。
     */
    private static final String EXPECTED_AUDIENCE = "gateway";

    /**
     * JWT Payload 中 iss 的固定签发方，标识 token 由商户服务端签发。
     */
    private static final String EXPECTED_ISSUER = "merchant";

    /**
     * JWT 最大有效时间窗口，单位秒，当前按支付接口要求限制为 3 分钟。
     */
    private static final long MAX_TOKEN_SECONDS = 180L;

    /**
     * 允许的服务器与商户侧时钟偏移，单位秒，用于降低轻微时间漂移导致的误拒绝。
     */
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
            throw new ApiException(ApiResultEnum.MERCHANT_INVALID);
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
            throw new ApiException(ApiResultEnum.MERCHANT_SIGNING_KEY_NOT_CONFIGURED);
        }
        JWT jwt = parseToken(token);
        validateHeader(jwt);
        validateSignature(jwt, merchantKey);
        return validatePayload(jwt, nowEpochSeconds);
    }

    /**
     * 解析商户 JWT；缺失和格式错误分别映射为稳定公开错误码。
     *
     * @param token 商户 Bearer JWT
     * @return 已解析 JWT
     */
    private JWT parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
        }
        try {
            return JWTUtil.parseToken(token);
        } catch (RuntimeException exception) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_INVALID);
        }
    }

    /**
     * 校验 JWT 类型和算法，拒绝算法降级或类型混淆。
     *
     * @param jwt 已解析 JWT
     */
    private void validateHeader(JWT jwt) {
        if (!JWT_TYPE.equalsIgnoreCase(asString(jwt.getHeader(JWTHeader.TYPE)))) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_INVALID);
        }
        if (!JWT_ALGORITHM.equalsIgnoreCase(asString(jwt.getHeader(JWTHeader.ALGORITHM)))) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_INVALID);
        }
    }

    /**
     * 使用当前商户 HS256 密钥验证 JWT 签名。
     *
     * @param jwt 已解析 JWT
     * @param merchantKey 商户签名密钥
     */
    private void validateSignature(JWT jwt, String merchantKey) {
        JWTSigner signer = JWTSignerUtil.hs256(merchantKey.getBytes(StandardCharsets.UTF_8));
        if (!jwt.verify(signer)) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_SIGNATURE_INVALID);
        }
    }

    /**
     * 校验受众、签发方、商户身份、JWT ID 和时间窗口并构造受控声明。
     *
     * @param jwt 已验签 JWT
     * @param nowEpochSeconds 当前秒级时间
     * @return 已校验商户声明
     */
    private JwtMerchantClaims validatePayload(JWT jwt, long nowEpochSeconds) {
        validateAudience(jwt.getPayload(RegisteredPayload.AUDIENCE));
        if (!EXPECTED_ISSUER.equals(asString(jwt.getPayload(RegisteredPayload.ISSUER)))) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_ISS_INVALID);
        }
        String jwtId = asString(jwt.getPayload(RegisteredPayload.JWT_ID));
        String merchantId = asString(jwt.getPayload("merchantId"));
        if (!StringUtils.hasText(jwtId)) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_INVALID);
        }
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.MERCHANT_INVALID);
        }
        long issuedAt = toEpochSeconds(jwt.getPayload(RegisteredPayload.ISSUED_AT), ApiResultEnum.AUTHORIZATION_JWT_IAT_INVALID);
        long expiresAt = toEpochSeconds(jwt.getPayload(RegisteredPayload.EXPIRES_AT), ApiResultEnum.AUTHORIZATION_JWT_EXPIRED);
        validateTimeWindow(issuedAt, expiresAt, nowEpochSeconds);

        JwtMerchantClaims claims = new JwtMerchantClaims();
        claims.setMerchantId(merchantId);
        claims.setJwtId(jwtId);
        claims.setIssuedAt(issuedAt);
        claims.setExpiresAt(expiresAt);
        return claims;
    }

    /**
     * 校验 audience 字符串或集合包含平台固定受众。
     *
     * @param audiences JWT audience 原始值
     */
    private void validateAudience(Object audiences) {
        if (audiences instanceof Collection<?> audienceCollection && audienceCollection.contains(EXPECTED_AUDIENCE)) {
            return;
        }
        if (EXPECTED_AUDIENCE.equals(asString(audiences))) {
            return;
        }
        throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_AUD_INVALID);
    }

    /**
     * 将 Instant、数字或数字文本统一转换为秒级时间戳，并兼容毫秒输入。
     *
     * @param value JWT 时间声明原始值
     * @param errorCode 转换失败时使用的稳定错误码
     * @return 秒级 Unix 时间戳
     */
    private long toEpochSeconds(Object value, ApiResultEnum errorCode) {
        if (value instanceof Instant instant) {
            return instant.getEpochSecond();
        }
        if (value instanceof Number number) {
            long timestamp = number.longValue();
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

    /**
     * 校验签发、过期、最长有效期和允许的时钟偏差。
     *
     * @param issuedAt 签发秒级时间
     * @param expiresAt 过期秒级时间
     * @param nowEpochSeconds 当前秒级时间
     */
    private void validateTimeWindow(long issuedAt, long expiresAt, long nowEpochSeconds) {
        if (issuedAt <= 0L || expiresAt <= 0L || expiresAt <= issuedAt) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_EXPIRED);
        }
        if (expiresAt - issuedAt > MAX_TOKEN_SECONDS) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_EXPIRED);
        }
        if (issuedAt > nowEpochSeconds + ALLOWED_CLOCK_SKEW_SECONDS) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_IAT_INVALID);
        }
        if (expiresAt <= nowEpochSeconds) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_EXPIRED);
        }
    }

    /**
     * 将 JWT 声明值安全转换为文本并保留 null。
     *
     * @param value 声明原始值
     * @return 文本值或 null
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
