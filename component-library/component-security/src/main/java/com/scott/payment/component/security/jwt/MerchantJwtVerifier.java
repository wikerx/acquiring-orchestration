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
     * 解析parsetoken，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 公共组件库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param token 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 构造、转换或解析后的业务值
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
     * 校验请求头输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param jwt JWT 输入值，参与 jwt 的查询、校验、转换、写入或日志摘要
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
     * 校验signature输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param jwt JWT 输入值，参与 jwt 的查询、校验、转换、写入或日志摘要
     * @param merchantKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     */
    private void validateSignature(JWT jwt, String merchantKey) {
        JWTSigner signer = JWTSignerUtil.hs256(merchantKey.getBytes(StandardCharsets.UTF_8));
        if (!jwt.verify(signer)) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_SIGNATURE_INVALID);
        }
    }

    /**
     * 校验payload输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param jwt JWT 输入值，参与 jwt 的查询、校验、转换、写入或日志摘要
     * @param nowEpochSeconds now Epoch Seconds 输入值，参与 nowepochseconds 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 校验audience输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param audiences audiences 输入值，参与 audiences 的查询、校验、转换、写入或日志摘要
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
     * 构造epochseconds对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param errorCode error Code 输入值，参与 错误编码 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 校验时间window输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 公共组件库 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param issuedAt issued At 输入值，参与 issuedat 的查询、校验、转换、写入或日志摘要
     * @param expiresAt expires At 输入值，参与 expiresat 的查询、校验、转换、写入或日志摘要
     * @param nowEpochSeconds now Epoch Seconds 输入值，参与 nowepochseconds 的查询、校验、转换、写入或日志摘要
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
     * 规范化asstring，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
