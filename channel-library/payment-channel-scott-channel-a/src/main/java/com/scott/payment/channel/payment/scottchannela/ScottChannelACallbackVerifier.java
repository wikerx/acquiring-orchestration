package com.scott.payment.channel.payment.scottchannela;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
public class ScottChannelACallbackVerifier {

    public static final String MERCHANT_ID_HEADER = "X-Merchant-Id";
    public static final String EVENT_ID_HEADER = "X-Callback-Event-Id";
    public static final String SIGNATURE_HEADER = "X-Callback-Signature";

    /** Verify the exact HTTP entity bytes without a String re-encoding round trip. */
    public void verify(RawChannelCallbackRequest request,
                       ChannelCallbackVerificationContext context) {
        String merchantId = requiredHeader(request, MERCHANT_ID_HEADER);
        requiredHeader(request, EVENT_ID_HEADER);
        String actualSignature = requiredHeader(request, SIGNATURE_HEADER);
        if (context.defaultSecret() == null || context.defaultSecret().isBlank()) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "ScottChannelA callback secret is not configured");
        }
        String expectedSignature;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(context.defaultSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            expectedSignature = HexFormat.of().formatHex(mac.doFinal(request.body()));
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

    private String requiredHeader(RawChannelCallbackRequest request, String name) {
        String value = request.firstHeader(name);
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
