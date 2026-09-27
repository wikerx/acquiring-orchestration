package com.scott.payment.channel.payment.scottchannela;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackVerifier;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelACallbackVerifier
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 原始请求体 HMAC-SHA256 回调验签器，严格使用 raw body UTF-8 字节和渠道规定的小写十六进制签名。
 * @status : create
 */
public class ScottChannelACallbackVerifier implements PaymentChannelCallbackVerifier {

    public static final String MERCHANT_ID_HEADER = "X-Merchant-Id";
    public static final String EVENT_ID_HEADER = "X-Callback-Event-Id";
    public static final String SIGNATURE_HEADER = "X-Callback-Signature";

    /** 返回该验签器支持的渠道编码集合。 */
    @Override
    public Set<String> channelCodes() {
        return Set.of(ScottChannelACode.CODE);
    }

    /** 使用 MID 级回调密钥对 raw body 执行 HMAC-SHA256 验签。 */
    @Override
    public void verify(ChannelCallbackVerificationRequest request) {
        String merchantId = requiredHeader(request, MERCHANT_ID_HEADER);
        requiredHeader(request, EVENT_ID_HEADER);
        String actualSignature = requiredHeader(request, SIGNATURE_HEADER);
        if (request.defaultSecret() == null || request.defaultSecret().isBlank()) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "ScottChannelA callback secret is not configured");
        }
        String expectedSignature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(request.defaultSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((request.rawBody() == null ? "" : request.rawBody())
                    .getBytes(StandardCharsets.UTF_8));
            expectedSignature = HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw failure(ChannelCallbackVerificationException.Reason.INTERNAL_ERROR,
                    "ScottChannelA callback signature could not be calculated");
        }
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.US_ASCII),
                actualSignature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw failure(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                    "ScottChannelA callback signature is invalid for merchant " + merchantId);
        }
    }

    /** ScottChannelA 只接受正文为 SUCCESS 的回调确认响应。 */
    @Override
    public boolean requiresPlainTextSuccessAcknowledgement() {
        return true;
    }

    private String requiredHeader(ChannelCallbackVerificationRequest request, String name) {
        String value = request.header(name);
        if (value == null || value.isBlank()) {
            throw failure(ChannelCallbackVerificationException.Reason.HEADER_MISSING,
                    "ScottChannelA callback header is missing: " + name);
        }
        return value.trim();
    }

    private ChannelCallbackVerificationException failure(ChannelCallbackVerificationException.Reason reason,
                                                         String message) {
        return new ChannelCallbackVerificationException(reason, message);
    }
}
