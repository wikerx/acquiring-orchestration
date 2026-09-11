package com.scott.payment.component.web.gateway;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayIngressAuthFilterTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证受保护收银台路径只接受 service-gateway 签发的短时请求。
 * @status : create
 */
class GatewayIngressAuthFilterTests {

    private static final long NOW = 1786176000000L;
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    /** 未携带网关签名的收银台直连请求必须在控制器之前被拒绝。 */
    @Test
    void shouldRejectDirectCheckoutRequest() throws Exception {
        GatewayIngressAuthFilter filter = filter(properties(SECRET));
        MockHttpServletRequest request = request("POST", "/checkout/api/v1/payment/submit", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertEquals(401, response.getStatus());
        assertFalse(invoked.get());
        assertTrue(response.getContentAsString().contains("service-gateway"));
    }

    /** 合法签名必须放行，且签名覆盖原始查询串。 */
    @Test
    void shouldAllowFreshGatewaySignedRequest() throws Exception {
        GatewayIngressAuthFilter filter = filter(properties(SECRET));
        MockHttpServletRequest request = request("POST", "/checkout/api/v1/session/query", "lang=zh-CN");
        sign(request, NOW, "nonce-001", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    /** 即使签名正确，超过允许时间窗的请求也不能直达收银台控制器。 */
    @Test
    void shouldRejectExpiredGatewaySignature() throws Exception {
        GatewayIngressAuthFilter filter = filter(properties(SECRET));
        MockHttpServletRequest request = request("POST", "/api/rest/checkout/v1/session", null);
        sign(request, NOW - 120_000L, "nonce-old", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("expired request must not reach controller");
        });

        assertEquals(401, response.getStatus());
    }

    /** 保护路径已开启但密钥未配置时必须失败关闭，不能退化为直连放行。 */
    @Test
    void shouldFailClosedWhenSecretIsMissing() throws Exception {
        GatewayIngressAuthFilter filter = filter(properties(""));
        MockHttpServletRequest request = request("GET", "/checkout/config/countries", null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("unconfigured protected request must not reach controller");
        });

        assertEquals(503, response.getStatus());
    }

    /** 其他业务路径不属于收银台入口，不得因 Gateway 密钥配置而被公共过滤器拦截。 */
    @Test
    void shouldLeaveNonCheckoutPathsUnchanged() throws Exception {
        GatewayIngressAuthFilter filter = filter(properties(""));
        MockHttpServletRequest request = request("POST", "/admin/auth/login", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldRejectRepeatedGatewayNonce() throws Exception {
        GatewayIngressAuthFilter filter = new GatewayIngressAuthFilter(
                properties(SECRET), (caller, nonce, ttl) -> false, () -> NOW);
        MockHttpServletRequest request = request("POST", "/api/rest/payment/v1/payment", null);
        sign(request, NOW, "nonce-repeated", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("repeated request must not reach controller");
        });

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectBodyDigestMismatch() throws Exception {
        GatewayIngressAuthFilter filter = filter(properties(SECRET));
        MockHttpServletRequest request = request("POST", "/api/rest/payment/v1/payment", null);
        byte[] signedBody = "{\"amount\":\"10.00\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"amount\":\"99.00\"}".getBytes(StandardCharsets.UTF_8);
        request.setContent(tamperedBody);
        request.setAttribute(GatewayIngressRequestBodyFilter.BODY_SHA256_ATTRIBUTE,
                GatewayIngressSignature.payloadSha256(tamperedBody));
        sign(request, NOW, "nonce-tampered", GatewayIngressSignature.payloadSha256(signedBody), SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("tampered request must not reach controller");
        });

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectTrustedClientIpTampering() throws Exception {
        GatewayIngressAuthFilter filter = filter(properties(SECRET));
        MockHttpServletRequest request = request("POST", "/api/rest/payment/v1/payment", null);
        request.addHeader(GatewayIngressSignature.HEADER_CLIENT_IP, "203.0.113.10");
        sign(request, NOW, "nonce-client-ip", SECRET);
        request.removeHeader(GatewayIngressSignature.HEADER_CLIENT_IP);
        request.addHeader(GatewayIngressSignature.HEADER_CLIENT_IP, "203.0.113.11");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("tampered client ip must not reach controller");
        });

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldRejectLegacySignatureByDefault() throws Exception {
        GatewayIngressAuthFilter filter = filter(properties(SECRET));
        MockHttpServletRequest request = request("POST", "/api/rest/payment/v1/payment", null);
        signLegacy(request, NOW, "nonce-legacy-default", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("legacy request must not reach controller by default");
        });

        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldAllowLegacySignatureWhenExplicitlyEnabled() throws Exception {
        GatewayIngressAuthProperties properties = properties(SECRET);
        properties.setAcceptLegacySignature(true);
        GatewayIngressAuthFilter filter = filter(properties);
        MockHttpServletRequest request = request("POST", "/api/rest/payment/v1/payment", null);
        signLegacy(request, NOW, "nonce-legacy-enabled", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    private GatewayIngressAuthFilter filter(GatewayIngressAuthProperties properties) {
        return new GatewayIngressAuthFilter(properties, (caller, nonce, ttl) -> true, () -> NOW);
    }

    private GatewayIngressAuthProperties properties(String secret) {
        GatewayIngressAuthProperties properties = new GatewayIngressAuthProperties();
        properties.setSecret(secret);
        properties.setAllowedClockSkewMillis(60_000L);
        return properties;
    }

    private MockHttpServletRequest request(String method, String path, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setQueryString(query);
        return request;
    }

    private void sign(MockHttpServletRequest request, long timestamp, String nonce, String secret) {
        String target = GatewayIngressSignature.requestTarget(request.getRequestURI(), request.getQueryString());
        Object digestAttribute = request.getAttribute(GatewayIngressRequestBodyFilter.BODY_SHA256_ATTRIBUTE);
        String bodyDigest = digestAttribute instanceof String value
                ? value : GatewayIngressSignature.payloadSha256(request.getContentAsByteArray());
        request.setAttribute(GatewayIngressRequestBodyFilter.BODY_SHA256_ATTRIBUTE, bodyDigest);
        sign(request, timestamp, nonce, bodyDigest, secret);
    }

    private void sign(MockHttpServletRequest request,
                      long timestamp,
                      String nonce,
                      String bodyDigest,
                      String secret) {
        String target = GatewayIngressSignature.requestTarget(request.getRequestURI(), request.getQueryString());
        request.addHeader(GatewayIngressSignature.HEADER_CALLER, GatewayIngressSignature.CALLER_SERVICE_GATEWAY);
        request.addHeader(GatewayIngressSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(GatewayIngressSignature.HEADER_NONCE, nonce);
        request.addHeader(GatewayIngressSignature.HEADER_BODY_SHA256, bodyDigest);
        request.addHeader(GatewayIngressSignature.HEADER_SIGNATURE,
                GatewayIngressSignature.sign(
                        request.getMethod(), target, timestamp, nonce, bodyDigest,
                        request.getHeader(GatewayIngressSignature.HEADER_CLIENT_IP), secret));
    }

    private void signLegacy(MockHttpServletRequest request, long timestamp, String nonce, String secret) {
        String target = GatewayIngressSignature.requestTarget(request.getRequestURI(), request.getQueryString());
        request.addHeader(GatewayIngressSignature.HEADER_CALLER, GatewayIngressSignature.CALLER_SERVICE_GATEWAY);
        request.addHeader(GatewayIngressSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(GatewayIngressSignature.HEADER_NONCE, nonce);
        request.addHeader(GatewayIngressSignature.HEADER_SIGNATURE,
                GatewayIngressSignature.sign(request.getMethod(), target, timestamp, nonce, secret));
    }
}
