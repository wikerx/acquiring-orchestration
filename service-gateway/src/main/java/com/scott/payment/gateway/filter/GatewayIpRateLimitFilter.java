package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.redis.rate.ReactiveRedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.gateway.config.GatewayRateLimitProperties;
import com.scott.payment.gateway.support.GatewayOpenApiErrorWriter;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 商户 OpenAPI 网关 IP 总量限流器，对同一客户端访问全部 /api/rest 接口的流量统一计数
 */
@Slf4j
@Component
public class GatewayIpRateLimitFilter implements GlobalFilter, Ordered {

    /**
     * {@code OPENAPI_PATH_PREFIX}，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String OPENAPI_PATH_PREFIX = "/api/rest/";
    /**
     * {@code RATE_LIMIT_SCOPE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final String RATE_LIMIT_SCOPE = "openapiIp";
    /**
     * {@code UNKNOWN_CLIENT_IP}常量，统一 {@code GatewayIpRateLimitFilter} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String UNKNOWN_CLIENT_IP = "unknown";
    private final ReactiveRedisSlidingWindowRateLimiter rateLimiter;
    private final GatewayRateLimitProperties properties;
    private final GatewayOpenApiErrorWriter errorWriter;

    public GatewayIpRateLimitFilter(ReactiveRedisSlidingWindowRateLimiter rateLimiter,
                                    GatewayRateLimitProperties properties,
                                    GatewayOpenApiErrorWriter errorWriter) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.errorWriter = errorWriter;
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
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        log.warn("event: GATEWAY_IP_RATE_LIMITED traceId: {} path: {} retryAfterSeconds: {}",
                exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER),
                exchange.getRequest().getURI().getPath(), retryAfterSeconds);
        return errorWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                OpenApiResultCode.TOO_MANY_REQUESTS);
    }
}
