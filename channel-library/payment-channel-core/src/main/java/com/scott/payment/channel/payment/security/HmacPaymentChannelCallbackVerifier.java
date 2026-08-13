package com.scott.payment.channel.payment.security;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackVerifier;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HmacPaymentChannelCallbackVerifier
 * @date : 2026-08-12 00:00
 * @description : 协议中立 HMAC-SHA256 回调验签基类，统一平台签名原文和 keyId/SHA256/Event-Signature 兼容格式。
 * @status : create
 */
@Component
public class HmacPaymentChannelCallbackVerifier implements PaymentChannelCallbackVerifier {

    public static final String TIMESTAMP_HEADER = "X-Channel-Timestamp";
    public static final String NONCE_HEADER = "X-Channel-Nonce";
    public static final String SIGNATURE_HEADER = "X-Channel-Signature";
    public static final String EVENT_SIGNATURE_HEADER = "Event-Signature";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String LINE_SEPARATOR = "\n";

    private final Set<String> channelCodes;

    public HmacPaymentChannelCallbackVerifier() {
        this(Set.of());
    }

    protected HmacPaymentChannelCallbackVerifier(Set<String> channelCodes) {
        this.channelCodes = channelCodes == null ? Set.of() : Set.copyOf(channelCodes);
    }

    @Override
    public Set<String> channelCodes() {
        return channelCodes;
    }

    @Override
    public void verify(ChannelCallbackVerificationRequest request) {
        String eventSignature = request.header(EVENT_SIGNATURE_HEADER);
        if (hasText(eventSignature)) {
            verifyEventSignature(request, eventSignature);
            return;
        }
        verifyPlatformSignature(request);
    }

    private void verifyPlatformSignature(ChannelCallbackVerificationRequest request) {
        String timestampText = request.header(TIMESTAMP_HEADER);
        String nonce = request.header(NONCE_HEADER);
        String signature = request.header(SIGNATURE_HEADER);
        if (!hasText(request.channelCode()) || !hasText(timestampText) || !hasText(nonce) || !hasText(signature)) {
            throw failure(ChannelCallbackVerificationException.Reason.HEADER_MISSING,
                    "channel callback signature headers are required");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw failure(ChannelCallbackVerificationException.Reason.TIMESTAMP_INVALID,
                    "channel callback signature timestamp is invalid");
        }
        if (Math.abs(request.currentTimeMillis() - timestamp) > request.allowedClockSkewMillis()) {
            throw failure(ChannelCallbackVerificationException.Reason.TIMESTAMP_EXPIRED,
                    "channel callback signature timestamp is expired");
        }
        if (!hasText(request.defaultSecret())) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "channel callback secret is not configured");
        }
        String expected = hmacSha256(canonicalText(request, timestamp, nonce), request.defaultSecret());
        if (!constantTimeMatches(expected, signature)) {
            throw failure(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                    "channel callback signature is invalid");
        }
    }

    private void verifyEventSignature(ChannelCallbackVerificationRequest request, String header) {
        String[] segments = header.split("/", 3);
        if (segments.length != 3 || !hasText(segments[0]) || !hasText(segments[1]) || !hasText(segments[2])) {
            throw failure(ChannelCallbackVerificationException.Reason.HEADER_INVALID,
                    "channel callback event signature is invalid");
        }
        if (!"SHA256".equalsIgnoreCase(segments[1].trim())) {
            throw failure(ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED,
                    "channel callback event signature algorithm is unsupported");
        }
        String secret = eventSecret(request.eventSecrets(), segments[0].trim());
        if (!hasText(secret)) {
            secret = request.defaultSecret();
        }
        if (!hasText(secret)) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "channel callback event signature secret is not configured");
        }
        String expected = hmacSha256(request.rawBody() == null ? "" : request.rawBody(), secret);
        if (!constantTimeMatches(expected, segments[2].trim())) {
            throw failure(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                    "channel callback event signature is invalid");
        }
    }

    private String canonicalText(ChannelCallbackVerificationRequest request, long timestamp, String nonce) {
        return request.method().toUpperCase(Locale.ROOT)
                + LINE_SEPARATOR + request.path()
                + LINE_SEPARATOR + timestamp
                + LINE_SEPARATOR + nonce
                + LINE_SEPARATOR + request.channelCode().trim().toLowerCase(Locale.ROOT)
                + LINE_SEPARATOR + sha256Hex(request.rawBody());
    }

    private String eventSecret(Map<String, String> secrets, String keyId) {
        String direct = secrets.get(keyId);
        if (hasText(direct)) {
            return direct;
        }
        for (Map.Entry<String, String> entry : secrets.entrySet()) {
            if (keyId.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String hmacSha256(String text, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new ChannelCallbackVerificationException(
                    ChannelCallbackVerificationException.Reason.INTERNAL_ERROR,
                    "channel callback signature can not be calculated",
                    exception);
        }
    }

    private String sha256Hex(String rawBody) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ChannelCallbackVerificationException(
                    ChannelCallbackVerificationException.Reason.INTERNAL_ERROR,
                    "channel callback body digest can not be calculated",
                    exception);
        }
    }

    private boolean constantTimeMatches(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ChannelCallbackVerificationException failure(ChannelCallbackVerificationException.Reason reason,
                                                         String message) {
        return new ChannelCallbackVerificationException(reason, message);
    }
}
