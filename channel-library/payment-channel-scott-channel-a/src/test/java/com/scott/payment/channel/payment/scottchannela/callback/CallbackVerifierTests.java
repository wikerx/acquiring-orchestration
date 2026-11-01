package com.scott.payment.channel.payment.scottchannela.callback;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import com.scott.payment.channel.payment.scottchannela.support.ChannelCode;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ScottChannelA 原始请求体 HMAC 验签和必填 Header 测试。
 **/
class CallbackVerifierTests {

    private static final String SECRET = "callback-secret-for-test";
    private static final String BODY = "{\"status\":\"SUCCESS\",\"amount\":10.25}";

    private final CallbackVerifier verifier = new CallbackVerifier();

    /**
     * 验证签名严格基于完整原始 UTF-8 body，并接受十六进制签名大小写差异。
     **/
    @Test
    void shouldVerifyRawBodyHmacSignature() throws Exception {
        String signature = hmac(BODY, SECRET).toUpperCase();
        RawChannelCallbackRequest request = request(BODY, signature);

        assertThatCode(() -> verifier.verify(request, context(SECRET))).doesNotThrowAnyException();
    }

    /**
     * 验证 body 内容被修改后不能复用原签名通过验签。
     **/
    @Test
    void shouldRejectChangedRawBody() throws Exception {
        String signature = hmac(BODY, SECRET);
        RawChannelCallbackRequest request = request(BODY + " ", signature);

        assertThatThrownBy(() -> verifier.verify(request, context(SECRET)))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID));
    }

    /**
     * 验证渠道要求的 MID、事件 ID 和签名 Header 缺失时安全失败。
     **/
    @Test
    void shouldRejectMissingRequiredHeader() {
        RawChannelCallbackRequest request = new RawChannelCallbackRequest(
                ChannelCode.VALUE, ChannelCallbackScene.STANDARD, "POST",
                "/channel/v1/callbacks/SCOTT_CHANNEL_A", null,
                Map.of(CallbackVerifier.MERCHANT_ID_HEADER, List.of("MID-TEST")),
                BODY.getBytes(StandardCharsets.UTF_8), "127.0.0.1");

        assertThatThrownBy(() -> verifier.verify(request, context(SECRET)))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.HEADER_MISSING));
    }

    private RawChannelCallbackRequest request(String body, String signature) {
        return new RawChannelCallbackRequest(
                ChannelCode.VALUE, ChannelCallbackScene.STANDARD, "POST",
                "/channel/v1/callbacks/SCOTT_CHANNEL_A", null,
                Map.of(
                        CallbackVerifier.MERCHANT_ID_HEADER, List.of("MID-TEST"),
                        CallbackVerifier.EVENT_ID_HEADER, List.of("EVENT-001"),
                        CallbackVerifier.SIGNATURE_HEADER, List.of(signature)),
                body.getBytes(StandardCharsets.UTF_8), "127.0.0.1");
    }

    private ChannelCallbackVerificationContext context(String secret) {
        return new ChannelCallbackVerificationContext(
                secret, Map.of(), 300_000L, System.currentTimeMillis());
    }

    private String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
