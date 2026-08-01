package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.trace.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayTraceIdFilter
 * @date : 2026-07-26 15:20
 * @email : scott_x@163.com
 * @description : 网关 traceId 全局过滤器，负责校验或生成 X-Trace-Id，并透传到下游服务和响应头。
 * @status : create
 */
@Slf4j
@Component
public class GatewayTraceIdFilter implements GlobalFilter, Ordered {

    /**
     * 请求未匹配到 Gateway 路由时使用的审计占位值。
     */
    private static final String UNKNOWN_ROUTE = "unknown";

    /**
     * 为进入网关的请求建立 trace 上下文，并记录路由开始、完成和异常事件。
     * <p>
     * 外部 traceId 先经过统一格式校验；响应头和下游请求使用同一标识。Reactor 链结束时清理
     * 线程上下文，避免复用线程把一次请求的 traceId 泄漏到后续请求。
     * </p>
     *
     * @param exchange 当前 WebFlux 请求交换对象
     * @param chain    后续网关过滤器链
     * @return 路由处理完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        String traceId = TraceContext.resolveOrCreate(exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER));
        TraceContext.setTraceId(traceId);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TraceContext.TRACE_ID_HEADER, traceId))
                .build();
        exchange.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        log.info("event: GATEWAY_REQUEST_START traceId: {} method: {} path: {} queryKeys: {} clientIp: {} userAgent: {}",
                traceId,
                request.getMethod(),
                request.getURI().getPath(),
                request.getQueryParams().keySet(),
                request.getRemoteAddress() == null ? null : request.getRemoteAddress().getAddress().getHostAddress(),
                safeUserAgent(request.getHeaders().getFirst("User-Agent")));
        return chain.filter(exchange.mutate().request(request).build())
                .doOnTerminate(() -> {
                    TraceContext.setTraceId(traceId);
                    logRouteComplete(exchange, startNanos);
                })
                .doOnSuccess(ignored -> {
                    TraceContext.setTraceId(traceId);
                    logFinish(exchange, startNanos, null);
                })
                .doOnError(error -> {
                    TraceContext.setTraceId(traceId);
                    logFinish(exchange, startNanos, error);
                })
                .doFinally(signalType -> TraceContext.clear());
    }

    /**
     * 将 trace 过滤器放在客户端 IP 等业务过滤器之前，确保后续日志均具备 traceId。
     *
     * @return Gateway 全局过滤器顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    /**
     * 记录一次请求最终成功或失败的审计事件。
     * <p>
     * 日志只包含路由、状态、耗时和异常类型，不记录请求体、鉴权凭据或其他敏感报文。
     * </p>
     *
     * @param exchange   当前 WebFlux 请求交换对象
     * @param startNanos 请求进入网关时的单调时钟值
     * @param error      路由异常；正常完成时为 {@code null}
     */
    private void logFinish(ServerWebExchange exchange, long startNanos, Throwable error) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        String routeId = routeId(exchange);
        Integer statusCode = exchange.getResponse().getStatusCode() == null
                ? null
                : exchange.getResponse().getStatusCode().value();
        String targetService = targetService(exchange);
        String traceId = TraceContext.getTraceId();
        if (error == null) {
            log.info("event: GATEWAY_REQUEST_END traceId: {} routeId: {} targetService: {} status: {} durationMs: {}",
                    traceId, routeId, targetService, statusCode, durationMs);
            return;
        }
        log.warn("event: GATEWAY_REQUEST_ERROR traceId: {} routeId: {} targetService: {} status: {} durationMs: {} errorType: {} message: {}",
                traceId,
                routeId,
                targetService,
                statusCode,
                durationMs,
                error.getClass().getSimpleName(),
                error.getMessage(),
                error);
    }

    /**
     * 记录网关路由链执行完成事件。
     *
     * @param exchange   WebFlux 请求交换对象
     * @param startNanos 请求进入网关的纳秒时间
     */
    private void logRouteComplete(ServerWebExchange exchange, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("event: GATEWAY_ROUTE_COMPLETE traceId: {} routeId: {} targetService: {} path: {} durationMs: {}",
                TraceContext.getTraceId(),
                routeId(exchange),
                targetService(exchange),
                exchange.getRequest().getURI().getPath(),
                durationMs);
    }

    /**
     * 提取 Gateway 路由标识，未匹配路由时返回 unknown。
     *
     * @param exchange WebFlux 请求交换对象
     * @return 路由标识
     */
    private String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return route == null ? UNKNOWN_ROUTE : route.getId();
    }

    /**
     * 提取 Gateway 路由目标服务，优先使用服务发现 URI 的 host，未匹配时返回 unknown。
     *
     * @param exchange WebFlux 请求交换对象
     * @return 下游目标服务名或网关内部转发地址摘要
     */
    private String targetService(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null || route.getUri() == null) {
            return UNKNOWN_ROUTE;
        }
        if (StringUtils.hasText(route.getUri().getHost())) {
            return route.getUri().getHost();
        }
        return route.getUri().getSchemeSpecificPart();
    }

    /**
     * 截断 User-Agent 后再写入入口日志，控制单条日志大小。
     *
     * @param userAgent 请求头中的 User-Agent
     * @return 最长 120 个字符的日志值；原值为空时返回 {@code null}
     */
    private String safeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }
}
