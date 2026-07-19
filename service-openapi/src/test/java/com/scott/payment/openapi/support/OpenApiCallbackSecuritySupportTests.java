package com.scott.payment.openapi.support;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_NONCE_HEADER;
import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_SIGNATURE_HEADER;
import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_TIMESTAMP_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiCallbackSecuritySupportTests
 * @date : 2026-07-14 23:50
 * @email : scott_x@163.com
 * @description : 渠道回调安全校验测试，覆盖渠道签名必须绑定原始 body 摘要，避免回调业务报文被替换。
 * @status : create
 */
class OpenApiCallbackSecuritySupportTests {

    private static final String CHANNEL_CODE = "mpgs";
    private static final String SECRET = "test-channel-callback-secret";
    private static final String RAW_BODY = "{\"result\":\"SUCCESS\",\"response\":{\"acquirerCode\":\"00\"}}";

    /**
     * 验证渠道回调签名携带正确 body 摘要时可以通过校验。
     */
    @Test
    void shouldVerifyChannelCallbackSignatureWithBodyDigest() {
        OpenApiCallbackSecuritySupport support = new OpenApiCallbackSecuritySupport(properties(), mock(SecurityInterceptEventRecorder.class));
        MockHttpServletRequest request = signedRequest(RAW_BODY);

        OpenApiCallbackSecuritySupport.CallbackSecurityResult result =
                support.verifyChannelCallback(CHANNEL_CODE, request, RAW_BODY);

        assertThat(result.signatureValid()).isTrue();
        assertThat(result.ipAllowed()).isTrue();
    }

    /**
     * 验证签名和 body 不匹配时拒绝渠道回调，防止只替换业务报文仍通过验签。
     */
    @Test
    void shouldRejectChannelCallbackWhenBodyIsTampered() {
        OpenApiCallbackSecuritySupport support = new OpenApiCallbackSecuritySupport(properties(), mock(SecurityInterceptEventRecorder.class));
        MockHttpServletRequest request = signedRequest(RAW_BODY);

        assertThatThrownBy(() -> support.verifyChannelCallback(CHANNEL_CODE, request,
                "{\"result\":\"SUCCESS\",\"response\":{\"acquirerCode\":\"05\"}}"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("channel callback signature is invalid");
    }

    private OpenApiCallbackProperties properties() {
        OpenApiCallbackProperties properties = new OpenApiCallbackProperties();
        properties.setChannelSignatureRequired(true);
        properties.getChannelSecrets().put(CHANNEL_CODE, SECRET);
        return properties;
    }

    private MockHttpServletRequest signedRequest(String rawBody) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/channel/v1/callbacks/" + CHANNEL_CODE);
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String signature = InternalServiceSignature.sign(
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                nonce,
                CHANNEL_CODE,
                sha256Hex(rawBody),
                SECRET
        );
        request.addHeader(CHANNEL_TIMESTAMP_HEADER, String.valueOf(timestamp));
        request.addHeader(CHANNEL_NONCE_HEADER, nonce);
        request.addHeader(CHANNEL_SIGNATURE_HEADER, signature);
        return request;
    }

    private String sha256Hex(String rawBody) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
