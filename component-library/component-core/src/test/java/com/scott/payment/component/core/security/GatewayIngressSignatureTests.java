package com.scott.payment.component.core.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayIngressSignatureTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Gateway 入口签名在响应式网关和 Servlet 下游之间保持一致。
 * @status : create
 */
class GatewayIngressSignatureTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    /** 相同请求目标必须生成可验证签名，查询串变化必须导致验签失败。 */
    @Test
    void shouldBindSignatureToRawRequestTarget() {
        long timestamp = 1786176000000L;
        String nonce = "nonce-001";
        String target = GatewayIngressSignature.requestTarget("/checkout/api/v1/session/query", "lang=zh-CN");
        String signature = GatewayIngressSignature.sign("POST", target, timestamp, nonce, SECRET);

        assertTrue(GatewayIngressSignature.matches(signature,
                GatewayIngressSignature.sign("POST", target, timestamp, nonce, SECRET)));
        assertFalse(GatewayIngressSignature.matches(signature,
                GatewayIngressSignature.sign("POST", target + "&attempt=2", timestamp, nonce, SECRET)));
    }

    /** 四组收银台入口必须共享同一保护清单，其他业务路径不能被误判。 */
    @Test
    void shouldRecognizeEveryCheckoutBackendIngressPath() {
        assertTrue(GatewayIngressSignature.isProtectedCheckoutPath("/api/rest/checkout/v1/session"));
        assertTrue(GatewayIngressSignature.isProtectedCheckoutPath("/checkout/api/v1/payment/submit"));
        assertTrue(GatewayIngressSignature.isProtectedCheckoutPath("/checkout/config/countries"));
        assertTrue(GatewayIngressSignature.isProtectedCheckoutPath("/checkout/health"));
        assertFalse(GatewayIngressSignature.isProtectedCheckoutPath("/api/rest/payment/v1/payment"));
        assertFalse(GatewayIngressSignature.isProtectedCheckoutPath("/checkout-api/v1/session"));
    }
}
