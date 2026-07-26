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

    /**
     * SECRET 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；高敏感字段，禁止打印日志、禁止写入异常消息，持久化前需确认安全要求。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String SECRET = "test-internal-secret";
    /**
     * CALLER 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
