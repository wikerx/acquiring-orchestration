package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.redis.rate.ReactiveRedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.gateway.config.GatewayRateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayIpRateLimitFilter
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 网关 IP 总量限流器，对同一客户端访问全部 /api/rest 接口的流量统一计数
 * @status : create
 */
@Slf4j
@Component
public class GatewayIpRateLimitFilter implements GlobalFilter, Ordered {

    private static final String OPENAPI_PATH_PREFIX = "/api/rest/";
    private static final String RATE_LIMIT_SCOPE = "openapiIp";
    private static final String UNKNOWN_CLIENT_IP = "unknown";
    private static final byte[] RATE_LIMIT_BODY =
            "{\"code\":\"F429\",\"message\":\"Too many requests. Please retry later.\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);

    private final ReactiveRedisSlidingWindowRateLimiter rateLimiter;
    private final GatewayRateLimitProperties properties;

    public GatewayIpRateLimitFilter(ReactiveRedisSlidingWindowRateLimiter rateLimiter,
                                    GatewayRateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /**
     * 对商户开放接口执行客户端 IP 精确滑动窗口判定。
     * <p>
     * 预检请求不占用业务配额；客户端 IP 由前置可信代理过滤器写入，缺失时统一归入 unknown，
     * 避免异常连接通过空身份绕过限流。限流身份由组件摘要后写入 Redis，不保存原始 IP。
     * </p>
     *
     * @param exchange 当前 Gateway 请求上下文
     * @param chain    后续过滤器链
     * @return 允许时继续转发，超限时直接返回统一 429 响应
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!shouldLimit(exchange)) {
            return chain.filter(exchange);
        }
        String clientIp = exchange.getRequest().getHeaders()
                .getFirst(GatewayClientIpHeaderFilter.HEADER_GATEWAY_CLIENT_IP);
        String identity = StringUtils.hasText(clientIp) ? clientIp : UNKNOWN_CLIENT_IP;
        return rateLimiter.tryAcquire(
                        RATE_LIMIT_SCOPE,
                        identity,
                        properties.getMaxRequests(),
                        properties.getWindow()
                )
                .flatMap(result -> result.allowed()
                        ? chain.filter(exchange)
                        : writeRateLimited(exchange, result));
    }

    /**
     * 在客户端 IP 规范化之后、请求正文读取和下游路由之前执行。
     *
     * @return Gateway 全局过滤器顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 21;
    }

    private boolean shouldLimit(ServerWebExchange exchange) {
        return properties.isEnabled()
                && exchange.getRequest().getMethod() != HttpMethod.OPTIONS
                && exchange.getRequest().getURI().getRawPath().startsWith(OPENAPI_PATH_PREFIX);
    }

    private Mono<Void> writeRateLimited(ServerWebExchange exchange,
                                        SlidingWindowRateLimitResult result) {
        long retryAfterSeconds = Math.max(1L, result.retryAfterSeconds());
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        log.warn("event: GATEWAY_IP_RATE_LIMITED traceId: {} path: {} retryAfterSeconds: {}",
                exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER),
                exchange.getRequest().getURI().getPath(), retryAfterSeconds);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(RATE_LIMIT_BODY);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
