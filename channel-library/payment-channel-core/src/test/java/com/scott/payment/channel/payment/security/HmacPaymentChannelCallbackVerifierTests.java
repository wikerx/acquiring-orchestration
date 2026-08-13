package com.scott.payment.channel.payment.security;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HmacPaymentChannelCallbackVerifierTests
 * @date : 2026-08-12 00:00
 * @description : 协议中立回调验签测试，锁定平台签名原文、Event-Signature 和稳定失败分类。
 * @status : create
 */
class HmacPaymentChannelCallbackVerifierTests {

    private static final String SECRET = "callback-test-secret";
    private static final String BODY = "{\"result\":\"SUCCESS\"}";
    private static final long NOW = 1_800_000_000_000L;

    @Test
    void shouldVerifyPlatformSignature() {
        String nonce = "nonce-001";
        String canonical = "POST\n/channel/v1/callbacks/MPGS\n" + NOW + "\n" + nonce
                + "\nmpgs\n" + sha256(BODY);
        ChannelCallbackVerificationRequest request = request(Map.of(
                HmacPaymentChannelCallbackVerifier.TIMESTAMP_HEADER, String.valueOf(NOW),
                HmacPaymentChannelCallbackVerifier.NONCE_HEADER, nonce,
                HmacPaymentChannelCallbackVerifier.SIGNATURE_HEADER, hmac(canonical, SECRET)
        ), Map.of());

        assertThatCode(() -> new HmacPaymentChannelCallbackVerifier().verify(request)).doesNotThrowAnyException();
    }

    @Test
    void shouldVerifyEventSignatureByKeyIdIgnoringCase() {
        ChannelCallbackVerificationRequest request = request(Map.of(
                HmacPaymentChannelCallbackVerifier.EVENT_SIGNATURE_HEADER,
                "key-001/SHA256/" + hmac(BODY, SECRET)
        ), Map.of("KEY-001", SECRET));

        assertThatCode(() -> new HmacPaymentChannelCallbackVerifier().verify(request)).doesNotThrowAnyException();
    }

    @Test
    void shouldClassifyExpiredTimestamp() {
        ChannelCallbackVerificationRequest request = request(Map.of(
                HmacPaymentChannelCallbackVerifier.TIMESTAMP_HEADER, String.valueOf(NOW - 301_000),
                HmacPaymentChannelCallbackVerifier.NONCE_HEADER, "nonce-002",
                HmacPaymentChannelCallbackVerifier.SIGNATURE_HEADER, "invalid"
        ), Map.of());

        assertThatThrownBy(() -> new HmacPaymentChannelCallbackVerifier().verify(request))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.TIMESTAMP_EXPIRED));
    }

    @Test
    void shouldClassifyUnsupportedEventAlgorithm() {
        ChannelCallbackVerificationRequest request = request(Map.of(
                HmacPaymentChannelCallbackVerifier.EVENT_SIGNATURE_HEADER, "key-001/SHA512/invalid"
        ), Map.of("key-001", SECRET));

        assertThatThrownBy(() -> new HmacPaymentChannelCallbackVerifier().verify(request))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED));
    }

    private ChannelCallbackVerificationRequest request(Map<String, String> headers,
                                                       Map<String, String> eventSecrets) {
        return new ChannelCallbackVerificationRequest("MPGS", "POST", "/channel/v1/callbacks/MPGS",
                headers, BODY, SECRET, eventSecrets, 300_000, NOW);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
