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

    private static final String ISSUER = "platform";
    private static final String AUDIENCE = "merchant-callback";
    private static final int MIN_SECRET_BYTES = 32;
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

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

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
