package com.scott.payment.component.web.internal;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.UUID;

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

    private static final String SECRET = "test-internal-secret";
    private static final String CALLER = "service-openapi";

    /**
     * 验证携带合法内部签名的请求可以访问 /internal/** 接口。
     *
     * @throws Exception 拦截器处理失败
     */
    @Test
    void shouldAllowRequestWhenSignatureIsValid() throws Exception {
        InternalServiceAuthInterceptor interceptor = new InternalServiceAuthInterceptor(properties());
        MockHttpServletRequest request = signedRequest(InternalServiceSignature.currentTimeMillis());
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
        InternalServiceAuthInterceptor interceptor = new InternalServiceAuthInterceptor(properties());
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
        InternalServiceAuthInterceptor interceptor = new InternalServiceAuthInterceptor(properties());
        MockHttpServletRequest request = signedRequest(InternalServiceSignature.currentTimeMillis() - Duration.ofMinutes(10).toMillis());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("internal service signature timestamp is expired");
    }

    private InternalServiceAuthProperties properties() {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        properties.setSecret(SECRET);
        properties.setAllowedClockSkew(Duration.ofMinutes(5));
        return properties;
    }

    private MockHttpServletRequest signedRequest(long timestamp) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/payment/authorization");
        String nonce = UUID.randomUUID().toString();
        String signature = InternalServiceSignature.sign(
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                nonce,
                CALLER,
                SECRET
        );
        request.addHeader(InternalServiceSignature.HEADER_CALLER, CALLER);
        request.addHeader(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        request.addHeader(InternalServiceSignature.HEADER_NONCE, nonce);
        request.addHeader(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return request;
    }
}
