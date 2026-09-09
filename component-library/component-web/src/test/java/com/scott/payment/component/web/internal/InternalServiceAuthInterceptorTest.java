package com.scott.payment.component.web.internal;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceAuthInterceptorTest
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 内部服务 HMAC 签名拦截器测试，覆盖签名通过、缺失请求头和过期时间窗。
 * @status : create
 */
class InternalServiceAuthInterceptorTest {

    private static final String SECRET = "test-internal-secret-at-least-32-bytes";
    private static final String PREVIOUS_SECRET = "previous-internal-secret-at-least-32-bytes";
    private static final String OTHER_SECRET = "another-internal-secret-at-least-32-bytes";
    private static final String CALLER = "service-openapi";

    /**
     * 验证携带合法内部签名的请求可以访问 /internal/** 接口。
     *
     * @throws Exception 拦截器处理失败
     */
    @Test
    void shouldAllowRequestWhenSignatureIsValid() throws Exception {
        InternalServiceAuthInterceptor interceptor = interceptorWithInMemoryReplayGuard();
        MockHttpServletRequest request = signedRequest(
                InternalServiceSignature.currentTimeMillis(), UUID.randomUUID().toString(), "limit=120", "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    /**
     * 验证缺少内部签名请求头时拒绝内部接口访问。
     *
     * @throws Exception 拦截器处理失败
     */
    @Test
    void shouldRejectRequestWhenSignatureHeadersAreMissing() throws Exception {
        InternalServiceAuthInterceptor interceptor = interceptorWithInMemoryReplayGuard();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/payment/authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("internal service signature headers are required");
    }

    /**
     * 验证超过时间窗的内部调用会被拒绝，降低签名被截获后重放的风险。
     *
     * @throws Exception 拦截器处理失败
     */
    @Test
    void shouldRejectRequestWhenTimestampIsExpired() throws Exception {
        InternalServiceAuthInterceptor interceptor = interceptorWithInMemoryReplayGuard();
        MockHttpServletRequest request = signedRequest(
                InternalServiceSignature.currentTimeMillis() - Duration.ofMinutes(10).toMillis(),
                UUID.randomUUID().toString(), null, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("internal service signature timestamp is expired");
    }

    /** 验证请求体摘要发生变化时，原签名不能继续使用。 */
    @Test
    void shouldRejectRequestWhenPayloadDigestIsTampered() throws Exception {
        InternalServiceAuthInterceptor interceptor = interceptorWithInMemoryReplayGuard();
        MockHttpServletRequest request = signedRequest(
                InternalServiceSignature.currentTimeMillis(), UUID.randomUUID().toString(), null, "{\"limit\":120}");
        request.setAttribute(InternalServiceRequestBodyFilter.BODY_SHA256_ATTRIBUTE,
                InternalServiceSignature.payloadSha256("{\"limit\":121}"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("internal service signature is invalid");
    }

    /** 验证查询参数发生变化时，原签名不能继续使用。 */
    @Test
    void shouldRejectRequestWhenQueryStringIsTampered() throws Exception {
        InternalServiceAuthInterceptor interceptor = interceptorWithInMemoryReplayGuard();
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        MockHttpServletRequest request = signedRequest(timestamp, nonce, "limit=120", "");
        request.setQueryString("limit=121");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("internal service signature is invalid");
    }

    /** 验证同一调用方和 nonce 的第二次请求会被拒绝。 */
    @Test
    void shouldRejectRequestWhenNonceIsReplayed() throws Exception {
        InternalServiceAuthInterceptor interceptor = interceptorWithInMemoryReplayGuard();
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        MockHttpServletRequest firstRequest = signedRequest(timestamp, nonce, null, "{}");
        MockHttpServletRequest repeatedRequest = signedRequest(timestamp, nonce, null, "{}");

        assertThat(interceptor.preHandle(firstRequest, new MockHttpServletResponse(), new Object())).isTrue();
        MockHttpServletResponse repeatedResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(repeatedRequest, repeatedResponse, new Object())).isFalse();
        assertThat(repeatedResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(repeatedResponse.getContentAsString()).contains("internal service request is replayed");
    }

    /** nonce TTL 配置过短时必须提升到两倍时钟偏差，完整覆盖签名可重放窗口。 */
    @Test
    void shouldEnforceNonceTtlSafetyFloor() throws Exception {
        InternalServiceAuthProperties properties = properties();
        properties.setNonceTtl(Duration.ofMinutes(1));
        AtomicReference<Duration> actualTtl = new AtomicReference<>();
        InternalServiceAuthInterceptor interceptor = new InternalServiceAuthInterceptor(
                properties,
                (caller, nonce, ttl) -> {
                    actualTtl.set(ttl);
                    return true;
                });
        MockHttpServletRequest request = signedRequest(
                InternalServiceSignature.currentTimeMillis(), UUID.randomUUID().toString(), null, "{}");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(actualTtl.get()).isEqualTo(Duration.ofMinutes(10));
    }

    /** 调用方独立密钥启用后，持有其他调用方密钥不能伪造当前调用方。 */
    @Test
    void shouldRejectRequestSignedWithAnotherCallersSecret() throws Exception {
        InternalServiceAuthProperties properties = callerScopedProperties();
        MockHttpServletRequest request = signedRequest(
                InternalServiceSignature.currentTimeMillis(), UUID.randomUUID().toString(), null, "{}", OTHER_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new InternalServiceAuthInterceptor(properties, (caller, nonce, ttl) -> true)
                .preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("internal service signature is invalid");
    }

    /** 轮换窗口内服务端同时接受 active 和 previous，调用方只继续使用 active。 */
    @Test
    void shouldAllowPreviousSecretDuringRotation() throws Exception {
        InternalServiceAuthProperties properties = callerScopedProperties();
        MockHttpServletRequest request = signedRequest(
                InternalServiceSignature.currentTimeMillis(), UUID.randomUUID().toString(), null, "{}", PREVIOUS_SECRET);

        boolean allowed = new InternalServiceAuthInterceptor(properties, (caller, nonce, ttl) -> true)
                .preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    /** 身份通过 HMAC 认证后仍必须受调用方路径授权约束，禁止横向调用其他内部命令。 */
    @Test
    void shouldRejectAuthenticatedCallerOutsideAllowedPaths() throws Exception {
        InternalServiceAuthProperties properties = callerScopedProperties();
        MockHttpServletRequest request = signedRequest(
                InternalServiceSignature.currentTimeMillis(), UUID.randomUUID().toString(), null, "{}", SECRET);
        request.setRequestURI("/internal/payment/refund-approvals/1/approve");
        resign(request, "{}", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new InternalServiceAuthInterceptor(properties, (caller, nonce, ttl) -> true)
                .preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsString()).contains("internal service caller is not allowed for this path");
    }

    private InternalServiceAuthProperties properties() {
        return callerScopedProperties();
    }

    private InternalServiceAuthProperties callerScopedProperties() {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        InternalServiceAuthProperties.CallerCredential credential = new InternalServiceAuthProperties.CallerCredential();
        credential.setActiveSecret(SECRET);
        credential.setPreviousSecret(PREVIOUS_SECRET);
        credential.setAllowedPaths(List.of("/internal/payment/authorization"));
        properties.setCallers(Map.of(CALLER, credential));
        properties.setAllowedClockSkew(Duration.ofMinutes(5));
        return properties;
    }

    private InternalServiceAuthInterceptor interceptorWithInMemoryReplayGuard() {
        Set<String> acquiredNonces = new HashSet<>();
        return new InternalServiceAuthInterceptor(properties(),
                (caller, nonce, ttl) -> acquiredNonces.add(caller + ':' + nonce));
    }

    private MockHttpServletRequest signedRequest(long timestamp, String nonce, String query, String body) {
        return signedRequest(timestamp, nonce, query, body, SECRET);
    }

    private MockHttpServletRequest signedRequest(long timestamp,
                                                 String nonce,
                                                 String query,
                                                 String body,
                                                 String secret) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/payment/authorization");
        request.setQueryString(query);
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        request.setContent(payload);
        String payloadSha256 = InternalServiceSignature.payloadSha256(payload);
        String signature = InternalServiceSignature.sign(
                request.getMethod(),
                InternalServiceSignature.requestTarget(request.getRequestURI(), request.getQueryString()),
                timestamp,
                nonce,
                CALLER,
                payloadSha256,
                secret
        );
        request.setAttribute(InternalServiceRequestBodyFilter.BODY_SHA256_ATTRIBUTE, payloadSha256);
        request.addHeader(InternalServiceSignature.HEADER_CALLER, CALLER);
        request.addHeader(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(InternalServiceSignature.HEADER_NONCE, nonce);
        request.addHeader(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return request;
    }

    private void resign(MockHttpServletRequest request, String body, String secret) {
        String payloadSha256 = InternalServiceSignature.payloadSha256(body);
        String signature = InternalServiceSignature.sign(
                request.getMethod(),
                InternalServiceSignature.requestTarget(request.getRequestURI(), request.getQueryString()),
                Long.parseLong(request.getHeader(InternalServiceSignature.HEADER_TIMESTAMP)),
                request.getHeader(InternalServiceSignature.HEADER_NONCE),
                request.getHeader(InternalServiceSignature.HEADER_CALLER),
                payloadSha256,
                secret
        );
        request.setAttribute(InternalServiceRequestBodyFilter.BODY_SHA256_ATTRIBUTE, payloadSha256);
        request.removeHeader(InternalServiceSignature.HEADER_SIGNATURE);
        request.addHeader(InternalServiceSignature.HEADER_SIGNATURE, signature);
    }
}
