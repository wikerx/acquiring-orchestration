package com.scott.payment.gateway.filter;

import com.scott.payment.component.redis.rate.ReactiveRedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.gateway.config.GatewayRateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Gateway IP 限流作用域、统一 429 响应和预检请求豁免。 */
class GatewayIpRateLimitFilterTests {

    @Test
    void shouldReturnStandard429WithoutForwardingRejectedRequest() {
        ReactiveRedisSlidingWindowRateLimiter limiter = mock(ReactiveRedisSlidingWindowRateLimiter.class);
        when(limiter.tryAcquire(eq("openapiIp"), eq("198.51.100.7"), eq(200), any(Duration.class)))
                .thenReturn(Mono.just(SlidingWindowRateLimitResult.rejected(17L)));
        GatewayIpRateLimitFilter filter = new GatewayIpRateLimitFilter(limiter, enabledProperties());
        MockServerWebExchange exchange = exchange("POST", "/api/rest/card-bin/v1/query");
        AtomicInteger forwarded = new AtomicInteger();

        filter.filter(exchange, current -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(forwarded).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("17");
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo(
                "{\"code\":\"F429\",\"message\":\"Too many requests. Please retry later.\",\"data\":null}");
    }

    @Test
    void shouldAggregateAllowedOpenApiRequestByTrustedClientIp() {
        ReactiveRedisSlidingWindowRateLimiter limiter = mock(ReactiveRedisSlidingWindowRateLimiter.class);
        when(limiter.tryAcquire(eq("openapiIp"), eq("198.51.100.7"), eq(200), any(Duration.class)))
                .thenReturn(Mono.just(SlidingWindowRateLimitResult.permitted()));
        GatewayIpRateLimitFilter filter = new GatewayIpRateLimitFilter(limiter, enabledProperties());
        AtomicInteger forwarded = new AtomicInteger();

        filter.filter(exchange("GET", "/api/rest/iso/v1/currencies/query"), current -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(forwarded).hasValue(1);
        verify(limiter).tryAcquire(eq("openapiIp"), eq("198.51.100.7"), eq(200),
                eq(Duration.ofMinutes(1)));
    }

    @Test
    void shouldExcludeCorsPreflightAndNonOpenApiRoutes() {
        ReactiveRedisSlidingWindowRateLimiter limiter = mock(ReactiveRedisSlidingWindowRateLimiter.class);
        GatewayIpRateLimitFilter filter = new GatewayIpRateLimitFilter(limiter, enabledProperties());
        AtomicInteger forwarded = new AtomicInteger();

        filter.filter(exchange("OPTIONS", "/api/rest/ip/v1/query"), current -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block();
        filter.filter(exchange("GET", "/admin/health"), current -> {
            forwarded.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(forwarded).hasValue(2);
        verify(limiter, never()).tryAcquire(any(), any(), any(Integer.class), any(Duration.class));
    }

    private GatewayRateLimitProperties enabledProperties() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequests(200);
        properties.setWindow(Duration.ofMinutes(1));
        return properties;
    }

    private MockServerWebExchange exchange(String method, String path) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(
                org.springframework.http.HttpMethod.valueOf(method), path);
        return MockServerWebExchange.from(builder
                .header(GatewayClientIpHeaderFilter.HEADER_GATEWAY_CLIENT_IP, "198.51.100.7")
                .build());
    }
}
