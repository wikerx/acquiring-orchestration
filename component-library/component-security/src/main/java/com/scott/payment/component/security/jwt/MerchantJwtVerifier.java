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
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantJwtVerifier
 * @date : 2026-05-28 11:42
 * @email : scott_x@163.com
 * @description : 商户 JWT HS256 验签器，负责校验开放接口 token 的头部、签名、aud/iss、有效期和 merchantId 声明。
 * @status : create
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
     * 解析 parse Token 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param token token 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析后的内部数据结构或业务值
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
     * 校验 validate Header 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param jwt jwt 输入值，含义由调用方法名称和所属业务对象限定
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
     * 校验 validate Signature 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param jwt jwt 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantKey merchant Key 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void validateSignature(JWT jwt, String merchantKey) {
        JWTSigner signer = JWTSignerUtil.hs256(merchantKey.getBytes(StandardCharsets.UTF_8));
        if (!jwt.verify(signer)) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_SIGNATURE_INVALID);
        }
    }

    /**
     * 校验 validate Payload 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param jwt jwt 输入值，含义由调用方法名称和所属业务对象限定
     * @param nowEpochSeconds now Epoch Seconds 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 校验 validate Audience 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param audiences audiences 输入值，含义由调用方法名称和所属业务对象限定
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
     * 转换生成 to Epoch Seconds 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param errorCode error Code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 校验 validate Time Window 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param issuedAt issued At 输入值，含义由调用方法名称和所属业务对象限定
     * @param expiresAt expires At 输入值，含义由调用方法名称和所属业务对象限定
     * @param nowEpochSeconds now Epoch Seconds 输入值，含义由调用方法名称和所属业务对象限定
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
     * 完成 as String 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
