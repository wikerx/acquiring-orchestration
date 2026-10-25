package com.scott.payment.component.web.gateway;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TrustedClientIpResolverTests
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : 验证下游服务只在 Gateway HMAC 验签成功后接受签名绑定的客户端 IP，否则使用 TCP 对端地址。
 * @status : create
 */
class TrustedClientIpResolverTests {

    /** 未经过入口验签时，外部伪造的专用头和常见代理头都不能覆盖 TCP 对端。 */
    @Test
    void shouldIgnoreSpoofedHeadersBeforeGatewayAuthentication() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.20");
        request.addHeader(GatewayIngressSignature.HEADER_CLIENT_IP, "198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("X-Real-IP", "203.0.113.11");

        assertThat(TrustedClientIpResolver.resolve(request)).isEqualTo("192.0.2.20");
    }

    /** HMAC 验签成功后，可以读取已被签名绑定的规范 IP。 */
    @Test
    void shouldUseGatewayClientIpAfterAuthentication() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader(GatewayIngressSignature.HEADER_CLIENT_IP, "2001:db8::7");
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);

        assertThat(TrustedClientIpResolver.resolve(request)).isEqualTo("2001:db8:0:0:0:0:0:7");
    }

    /** v2 验签头不是精确 IP 时必须拒绝该来源，不能把内部 Gateway 节点地址误当客户端 IP。 */
    @Test
    void shouldRejectSignedHeaderWhenItIsInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(GatewayIngressSignature.HEADER_CLIENT_IP, "203.0.113.10, 10.0.0.1");
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);

        assertThat(TrustedClientIpResolver.resolve(request)).isNull();
    }

    /** 旧签名只认证调用方，未绑定客户端 IP，不能使专用头成为可信来源。 */
    @Test
    void shouldIgnoreGatewayClientIpWhenOnlyLegacyRequestWasAuthenticated() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.30");
        request.addHeader(GatewayIngressSignature.HEADER_CLIENT_IP, "198.51.100.30");
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);

        assertThat(TrustedClientIpResolver.resolve(request)).isEqualTo("192.0.2.30");
    }
}
