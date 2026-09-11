package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.gateway.config.GatewayIngressProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
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
                .header(GatewayIngressSignature.HEADER_CLIENT_IP, "203.0.113.10")
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
        assertEquals(GatewayIngressSignature.sign(
                "POST", target, NOW, nonce, GatewayIngressSignature.EMPTY_BODY_SHA256,
                "203.0.113.10", SECRET), signature);
        assertEquals(GatewayIngressSignature.EMPTY_BODY_SHA256,
                forwarded.get().getRequest().getHeaders().getFirst(GatewayIngressSignature.HEADER_BODY_SHA256));
        assertNotEquals("forged", signature);
    }

    @Test
    void shouldBindSignatureToBodyAndReplayExactBytes() {
        byte[] body = "{\"amount\":\"10.00\"}".getBytes(StandardCharsets.UTF_8);
        CheckoutGatewayIngressFilter filter = filter(SECRET);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/rest/payment/v1/payment")
                .header(GatewayIngressSignature.HEADER_CLIENT_IP, "203.0.113.10")
                .body(new String(body, StandardCharsets.UTF_8)));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        DataBuffer forwardedBuffer = DataBufferUtils.join(forwarded.get().getRequest().getBody()).block();
        byte[] forwardedBody = new byte[forwardedBuffer.readableByteCount()];
        forwardedBuffer.read(forwardedBody);
        DataBufferUtils.release(forwardedBuffer);
        String digest = GatewayIngressSignature.payloadSha256(body);
        String signature = forwarded.get().getRequest().getHeaders()
                .getFirst(GatewayIngressSignature.HEADER_SIGNATURE);

        assertEquals(new String(body, StandardCharsets.UTF_8), new String(forwardedBody, StandardCharsets.UTF_8));
        assertEquals(digest, forwarded.get().getRequest().getHeaders()
                .getFirst(GatewayIngressSignature.HEADER_BODY_SHA256));
        assertEquals(GatewayIngressSignature.sign(
                "POST", "/api/rest/payment/v1/payment", NOW, "nonce-from-gateway", digest,
                "203.0.113.10", SECRET), signature);
    }

    /** 空正文也必须替换为可安全转发的 Publisher，避免下游再次订阅已消费的 Servlet 请求流。 */
    @Test
    void shouldReplayEmptyProtectedBodyWithoutResubscribingOriginalBody() {
        AtomicInteger subscriptions = new AtomicInteger();
        MockServerHttpRequest delegate = MockServerHttpRequest.get("/checkout/health").build();
        ServerHttpRequest singleSubscriptionRequest = new ServerHttpRequestDecorator(delegate) {
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.defer(() -> subscriptions.incrementAndGet() == 1
                        ? Flux.empty()
                        : Flux.error(new IllegalStateException("COMPLETED")));
            }
        };
        ServerWebExchange exchange = MockServerWebExchange.from(delegate)
                .mutate()
                .request(singleSubscriptionRequest)
                .build();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter(SECRET).filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        DataBufferUtils.join(forwarded.get().getRequest().getBody()).block();

        assertEquals(1, subscriptions.get());
        assertEquals(GatewayIngressSignature.EMPTY_BODY_SHA256,
                forwarded.get().getRequest().getHeaders()
                        .getFirst(GatewayIngressSignature.HEADER_BODY_SHA256));
    }

    @Test
    void shouldRejectOversizedProtectedBody() {
        GatewayIngressProperties properties = new GatewayIngressProperties();
        properties.setSecret(SECRET);
        properties.setMaxRequestBodyBytes(8);
        CheckoutGatewayIngressFilter filter = new CheckoutGatewayIngressFilter(
                properties, () -> NOW, () -> "nonce-from-gateway");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/rest/payment/v1/payment")
                .body("123456789"));

        filter.filter(exchange, current -> Mono.error(new AssertionError("oversized request was forwarded"))).block();

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exchange.getResponse().getStatusCode());
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
