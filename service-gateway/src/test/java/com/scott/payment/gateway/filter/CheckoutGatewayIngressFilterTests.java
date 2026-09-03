package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.gateway.config.GatewayIngressProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutGatewayIngressFilterTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Gateway 为收银台路由覆盖外部伪造头并签发可信入口凭证。
 * @status : create
 */
class CheckoutGatewayIngressFilterTests {

    private static final long NOW = 1786176000000L;
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    /** 受保护请求必须覆盖客户端伪造头并生成可由下游验证的签名。 */
    @Test
    void shouldReplaceSpoofedHeadersWithGatewaySignature() {
        CheckoutGatewayIngressFilter filter = filter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/checkout/api/v1/session/query?lang=zh-CN")
                .header(GatewayIngressSignature.HEADER_CALLER, "attacker")
                .header(GatewayIngressSignature.HEADER_SIGNATURE, "forged")
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        String timestamp = forwarded.get().getRequest().getHeaders()
                .getFirst(GatewayIngressSignature.HEADER_TIMESTAMP);
        String nonce = forwarded.get().getRequest().getHeaders().getFirst(GatewayIngressSignature.HEADER_NONCE);
        String signature = forwarded.get().getRequest().getHeaders().getFirst(GatewayIngressSignature.HEADER_SIGNATURE);
        String target = GatewayIngressSignature.requestTarget("/checkout/api/v1/session/query", "lang=zh-CN");
        assertEquals(GatewayIngressSignature.CALLER_SERVICE_GATEWAY,
                forwarded.get().getRequest().getHeaders().getFirst(GatewayIngressSignature.HEADER_CALLER));
        assertEquals(String.valueOf(NOW), timestamp);
        assertEquals("nonce-from-gateway", nonce);
        assertEquals(GatewayIngressSignature.sign("POST", target, NOW, nonce, SECRET), signature);
        assertNotEquals("forged", signature);
    }

    /** 非收银台路由不签名，但仍清除客户端伪造的内部入口头。 */
    @Test
    void shouldStripSpoofedHeadersOutsideCheckoutRoutes() {
        CheckoutGatewayIngressFilter filter = filter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/admin/auth/login")
                .header(GatewayIngressSignature.HEADER_SIGNATURE, "forged")
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        assertNull(forwarded.get().getRequest().getHeaders().getFirst(GatewayIngressSignature.HEADER_SIGNATURE));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst(GatewayIngressSignature.HEADER_CALLER));
    }

    /** 收银台入口未配置强密钥时必须由 Gateway 失败关闭。 */
    @Test
    void shouldFailClosedWhenGatewaySecretIsMissing() {
        CheckoutGatewayIngressFilter filter = filter("");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/checkout/config/countries").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertNull(forwarded.get());
        assertFalse(exchange.getResponse().getBodyAsString().block().isBlank());
    }

    private CheckoutGatewayIngressFilter filter(String secret) {
        GatewayIngressProperties properties = new GatewayIngressProperties();
        properties.setSecret(secret);
        return new CheckoutGatewayIngressFilter(properties, () -> NOW, () -> "nonce-from-gateway");
    }
}
