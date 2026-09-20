package com.scott.payment.channel.payment.scottchannela;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelACallbackVerifierTests
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 原始 body HMAC 回调验签测试，覆盖大小写兼容、正文不可重排和必需 Header 校验。
 * @status : create
 */
class ScottChannelACallbackVerifierTests {

    private static final String SECRET = "callback-secret-for-test";
    private static final String BODY = "{\"status\":\"SUCCESS\",\"amount\":10.25}";

    private final ScottChannelACallbackVerifier verifier = new ScottChannelACallbackVerifier();

    /** 验证签名严格基于完整原始 UTF-8 body，并接受十六进制签名大小写差异。 */
    @Test
    void shouldVerifyRawBodyHmacSignature() throws Exception {
        String signature = hmac(BODY, SECRET).toUpperCase();
        ChannelCallbackVerificationRequest request = request(BODY, signature, SECRET);

        assertThatCode(() -> verifier.verify(request)).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(verifier.requiresPlainTextSuccessAcknowledgement()).isTrue();
    }

    /** 验证 body 内容被修改后不能复用原签名通过验签。 */
    @Test
    void shouldRejectChangedRawBody() throws Exception {
        String signature = hmac(BODY, SECRET);
        ChannelCallbackVerificationRequest request = request(BODY + " ", signature, SECRET);

        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID));
    }

    /** 验证渠道要求的 MID、事件 ID 和签名 Header 缺失时安全失败。 */
    @Test
    void shouldRejectMissingRequiredHeader() {
        ChannelCallbackVerificationRequest request = new ChannelCallbackVerificationRequest(
                ScottChannelACode.CODE, "POST", "/channel/v1/callbacks/SCOTT_CHANNEL_A",
                Map.of(ScottChannelACallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST"), BODY,
                SECRET, Map.of(), 300_000L, System.currentTimeMillis());

        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.HEADER_MISSING));
    }

    private ChannelCallbackVerificationRequest request(String body, String signature, String secret) {
        return new ChannelCallbackVerificationRequest(
                ScottChannelACode.CODE, "POST", "/channel/v1/callbacks/SCOTT_CHANNEL_A",
                Map.of(
                        ScottChannelACallbackVerifier.MERCHANT_ID_HEADER, "MID-TEST",
                        ScottChannelACallbackVerifier.EVENT_ID_HEADER, "EVENT-001",
                        ScottChannelACallbackVerifier.SIGNATURE_HEADER, signature),
                body, secret, Map.of(), 300_000L, System.currentTimeMillis());
    }

    private String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
