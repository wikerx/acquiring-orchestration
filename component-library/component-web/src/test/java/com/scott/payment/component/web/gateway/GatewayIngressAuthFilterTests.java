package com.scott.payment.component.web.gateway;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证受保护收银台路径只接受 service-gateway 签发的短时请求。 */
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
        MockHttpServletRequest request = request("POST", "/api/rest/payment/v1/payment", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    private GatewayIngressAuthFilter filter(GatewayIngressAuthProperties properties) {
        return new GatewayIngressAuthFilter(properties, () -> NOW);
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
        request.addHeader(GatewayIngressSignature.HEADER_CALLER, GatewayIngressSignature.CALLER_SERVICE_GATEWAY);
        request.addHeader(GatewayIngressSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(GatewayIngressSignature.HEADER_NONCE, nonce);
        request.addHeader(GatewayIngressSignature.HEADER_SIGNATURE,
                GatewayIngressSignature.sign(request.getMethod(), target, timestamp, nonce, secret));
    }
}
