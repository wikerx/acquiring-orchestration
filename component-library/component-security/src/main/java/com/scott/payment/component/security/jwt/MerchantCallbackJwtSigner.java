package com.scott.payment.component.security.jwt;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.crypto.HmacSha256Signer;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 签发平台到商户的短时 HS256 回调 JWT，不记录或返回任何密钥摘要。 */
public class MerchantCallbackJwtSigner {

    /** 平台回调 JWT 签发者。 */
    private static final String ISSUER = "platform";
    /** 商户回调 JWT 固定受众。 */
    private static final String AUDIENCE = "merchant-callback";
    /** HS256 密钥最小字节数。 */
    private static final int MIN_SECRET_BYTES = 32;
    /** 回调密文 SHA-256 十六进制摘要格式。 */
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    /** 无状态 HMAC-SHA256 签名器，不保存商户密钥。 */
    private final HmacSha256Signer signer = new HmacSha256Signer();

    /**
     * 签发回调 JWT。eventId 同时写入 eventId 与 jti，商户必须校验二者和请求头一致。
     */
    public String sign(String merchantId,
                       String merchantSecret,
                       String eventId,
                       String notifyId,
                       String transactionId,
                       String payloadSha256,
                       int callbackTimes,
                       Instant issuedAt,
                       long ttlSeconds) {
        validate(merchantId, merchantSecret, eventId, notifyId, transactionId,
                payloadSha256, callbackTimes, issuedAt, ttlSeconds);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", ISSUER);
        payload.put("aud", List.of(AUDIENCE));
        payload.put("jti", eventId);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", issuedAt.plusSeconds(ttlSeconds).getEpochSecond());
        payload.put("merchantId", merchantId);
        payload.put("eventId", eventId);
        payload.put("notifyId", notifyId);
        payload.put("transactionId", transactionId);
        payload.put("payloadSha256", payloadSha256);
        payload.put("callbackTimes", callbackTimes);
        String signingInput = base64Url(JsonUtils.toJsonString(header)) + "." + base64Url(JsonUtils.toJsonString(payload));
        return signingInput + "." + signer.signBase64Url(signingInput, merchantSecret);
    }

    /**
     * 校验 JWT claims、安全摘要、尝试次数、有效期和密钥强度。
     *
     * @param merchantId 商户号
     * @param merchantSecret 商户回调 HMAC 密钥
     * @param eventId 本次回调事件号
     * @param notifyId 通知任务号
     * @param transactionId 平台交易号
     * @param payloadSha256 回调密文 SHA-256 摘要
     * @param callbackTimes 当前回调尝试次数
     * @param issuedAt JWT 签发时刻
     * @param ttlSeconds JWT 有效秒数
     */
    private void validate(String merchantId,
                          String merchantSecret,
                          String eventId,
                          String notifyId,
                          String transactionId,
                          String payloadSha256,
                          int callbackTimes,
                          Instant issuedAt,
                          long ttlSeconds) {
        if (!StringUtils.hasText(merchantId)
                || !StringUtils.hasText(eventId)
                || !StringUtils.hasText(notifyId)
                || !StringUtils.hasText(transactionId)
                || !StringUtils.hasText(payloadSha256)
                || !SHA256_HEX_PATTERN.matcher(payloadSha256).matches()
                || issuedAt == null
                || callbackTimes <= 0
                || ttlSeconds <= 0
                || ttlSeconds > 300) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "merchant callback JWT input is invalid");
        }
        if (!StringUtils.hasText(merchantSecret)
                || merchantSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new ServiceException(ApiResultEnum.MERCHANT_SIGNING_KEY_NOT_CONFIGURED.getCode(),
                    "merchant callback signing key is invalid");
        }
    }

    /**
     * 将 JWT JSON 片段编码为无填充 Base64URL。
     *
     * @param value JWT Header 或 Payload JSON
     * @return Base64URL 编码结果
     */
    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
