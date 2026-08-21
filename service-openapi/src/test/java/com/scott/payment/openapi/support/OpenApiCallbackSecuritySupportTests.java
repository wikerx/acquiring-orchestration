package com.scott.payment.openapi.support;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.channel.payment.api.PaymentChannelCallbackVerifier;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import com.scott.payment.channel.payment.registry.PaymentChannelCallbackVerifierRegistry;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_NONCE_HEADER;
import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_SIGNATURE_HEADER;
import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_TIMESTAMP_HEADER;
import static com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport.CHANNEL_EVENT_SIGNATURE_HEADER;
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

    /**
     * CHANNEL CODE，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String CHANNEL_CODE = "mpgs";
    private static final String EVENT_CHANNEL_CODE = "EVENT_PROVIDER";
    /**
     * SECRET，用于保存 Open API Callback Security Support Tests 中与 secret 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；高敏感字段，禁止明文打印日志，禁止写入异常消息。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String SECRET = "test-channel-callback-secret";
    private static final String RAW_BODY = "{\"result\":\"SUCCESS\",\"response\":{\"acquirerCode\":\"00\"}}";

    @Test
    void shouldPassProviderSpecificHeadersToCallbackVerifier() {
        AtomicReference<String> notificationSecret = new AtomicReference<>();
        PaymentChannelCallbackVerifier verifier = new PaymentChannelCallbackVerifier() {
            @Override
            public Set<String> channelCodes() {
                return Set.of("MPGS");
            }

            @Override
            public void verify(com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest request) {
                notificationSecret.set(request.header("X-Notification-Secret"));
            }
        };
        OpenApiCallbackSecuritySupport support = new OpenApiCallbackSecuritySupport(
                properties(), mock(SecurityInterceptEventRecorder.class),
                new PaymentChannelCallbackVerifierRegistry(List.of(verifier)));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/channel/v1/callbacks/MPGS/3ds");
        request.addHeader("X-Notification-Secret", SECRET);
        request.addHeader("X-Notification-ID", "notification-001");

        support.verifyChannelCallback("MPGS", request, RAW_BODY);

        assertThat(notificationSecret.get()).isEqualTo(SECRET);
    }

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
                .satisfies(throwable -> {
                    ApiException exception = (ApiException) throwable;
                    assertThat(exception.getCode()).isEqualTo(ApiResultEnum.UNAUTHORIZED.getCode());
                    assertThat(exception.getMessage()).isEqualTo("Unauthorized:SIGNATURE_INVALID");
                    assertThat(exception.getMessage()).doesNotContain("channel callback signature is invalid");
                });
    }

    /**
     * 配置事件密钥的渠道使用 keyId/SHA256/signature 头时应按原始 body 计算 HMAC-SHA256。
     */
    @Test
    void shouldVerifyConfiguredChannelEventSignature() {
        OpenApiCallbackProperties properties = properties();
        properties.getChannelEventSecrets().put(EVENT_CHANNEL_CODE, java.util.Map.of("KEY-001", SECRET));
        OpenApiCallbackSecuritySupport support = new OpenApiCallbackSecuritySupport(properties, mock(SecurityInterceptEventRecorder.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/channel/v1/callbacks/" + EVENT_CHANNEL_CODE);
        request.addHeader(CHANNEL_EVENT_SIGNATURE_HEADER, "KEY-001/SHA256/" + hmacSha256(RAW_BODY, SECRET));

        OpenApiCallbackSecuritySupport.CallbackSecurityResult result =
                support.verifyChannelCallback(EVENT_CHANNEL_CODE, request, RAW_BODY);

        assertThat(result.signatureValid()).isTrue();
        assertThat(result.ipAllowed()).isTrue();
    }

    /**
     * 渠道 Event-Signature 与回调原文不匹配时必须拒绝，避免伪造 CAPTURED 等终态通知。
     */
    @Test
    void shouldRejectChannelEventSignatureWhenBodyIsTampered() {
        OpenApiCallbackProperties properties = properties();
        properties.getChannelEventSecrets().put(EVENT_CHANNEL_CODE, java.util.Map.of("KEY-001", SECRET));
        OpenApiCallbackSecuritySupport support = new OpenApiCallbackSecuritySupport(properties, mock(SecurityInterceptEventRecorder.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/channel/v1/callbacks/" + EVENT_CHANNEL_CODE);
        request.addHeader(CHANNEL_EVENT_SIGNATURE_HEADER, "KEY-001/SHA256/" + hmacSha256(RAW_BODY, SECRET));

        assertThatThrownBy(() -> support.verifyChannelCallback(EVENT_CHANNEL_CODE, request,
                "{\"eventType\":\"sentForSettlement\",\"paymentId\":\"WP-PAY-002\"}"))
                .isInstanceOf(ApiException.class)
                .satisfies(throwable -> {
                    ApiException exception = (ApiException) throwable;
                    assertThat(exception.getCode()).isEqualTo(ApiResultEnum.UNAUTHORIZED.getCode());
                    assertThat(exception.getMessage()).isEqualTo("Unauthorized:SIGNATURE_INVALID");
                    assertThat(exception.getMessage()).doesNotContain("channel callback event signature is invalid");
                });
    }

    /** Provider 内部加密故障必须映射为 F500，不能伪装成外部未授权请求。 */
    @Test
    void shouldMapVerifierInternalErrorToInternalServerError() {
        PaymentChannelCallbackVerifier failingVerifier = new PaymentChannelCallbackVerifier() {
            @Override
            public Set<String> channelCodes() {
                return Set.of("MPGS");
            }

            @Override
            public void verify(com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest request) {
                throw new ChannelCallbackVerificationException(
                        ChannelCallbackVerificationException.Reason.INTERNAL_ERROR,
                        "channel callback signature can not be calculated");
            }
        };
        OpenApiCallbackSecuritySupport support = new OpenApiCallbackSecuritySupport(
                properties(),
                mock(SecurityInterceptEventRecorder.class),
                new PaymentChannelCallbackVerifierRegistry(List.of(failingVerifier)));
        MockHttpServletRequest request = signedRequest(RAW_BODY);

        assertThatThrownBy(() -> support.verifyChannelCallback(CHANNEL_CODE, request, RAW_BODY))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("F500"));
    }

    /**
     * 渠道配置 IP 白名单后，命中的 Gateway 可信客户端 IP 才允许进入后续回调处理。
     */
    @Test
    void shouldAllowConfiguredChannelCallbackIp() {
        OpenApiCallbackProperties properties = properties();
        properties.getChannelAllowedIps().put(CHANNEL_CODE, List.of("192.0.2.10"));
        OpenApiCallbackSecuritySupport support = new OpenApiCallbackSecuritySupport(properties, mock(SecurityInterceptEventRecorder.class));
        MockHttpServletRequest request = signedRequest(RAW_BODY);
        request.addHeader("X-Gateway-Client-Ip", "192.0.2.10");

        OpenApiCallbackSecuritySupport.CallbackSecurityResult result =
                support.verifyChannelCallback(CHANNEL_CODE, request, RAW_BODY);

        assertThat(result.ipAllowed()).isTrue();
    }

    /**
     * 渠道配置 IP 白名单后，非白名单来源必须拒绝，避免绕过渠道来源边界。
     */
    @Test
    void shouldRejectChannelCallbackWhenIpIsNotAllowed() {
        OpenApiCallbackProperties properties = properties();
        properties.getChannelAllowedIps().put(CHANNEL_CODE, List.of("192.0.2.10"));
        OpenApiCallbackSecuritySupport support = new OpenApiCallbackSecuritySupport(properties, mock(SecurityInterceptEventRecorder.class));
        MockHttpServletRequest request = signedRequest(RAW_BODY);
        request.addHeader("X-Gateway-Client-Ip", "198.51.100.20");

        assertThatThrownBy(() -> support.verifyChannelCallback(CHANNEL_CODE, request, RAW_BODY))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("channel callback source ip is not allowed");
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

    private String hmacSha256(String rawBody, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
