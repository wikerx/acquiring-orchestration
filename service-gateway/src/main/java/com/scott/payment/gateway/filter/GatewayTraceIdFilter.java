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
     * UNKNOWN ROUTE，用于保存 Gateway Trace ID Filter 中与 unknownroute 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String UNKNOWN_ROUTE = "unknown";

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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

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
     * 整理User-Agent 摘要，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 网关服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param userAgent user Agent 输入值，参与 User-Agent 摘要 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String safeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }
}
