package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.trace.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
     * UNKNOWN ROUTE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String UNKNOWN_ROUTE = "unknown";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        String traceId = TraceContext.resolveOrCreate(exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER));
        MDC.put(TraceContext.MDC_TRACE_ID_KEY, traceId);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TraceContext.TRACE_ID_HEADER, traceId))
                .build();
        exchange.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        log.info("event=GATEWAY_REQUEST_START method={} path={} queryKeys={} clientIp={} userAgent={}",
                request.getMethod(),
                request.getURI().getPath(),
                request.getQueryParams().keySet(),
                request.getRemoteAddress() == null ? null : request.getRemoteAddress().getAddress().getHostAddress(),
                safeUserAgent(request.getHeaders().getFirst("User-Agent")));
        return chain.filter(exchange.mutate().request(request).build())
                .doOnSuccess(ignored -> logFinish(exchange, startNanos, null))
                .doOnError(error -> logFinish(exchange, startNanos, error))
                .doFinally(signalType -> MDC.remove(TraceContext.MDC_TRACE_ID_KEY));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    private void logFinish(ServerWebExchange exchange, long startNanos, Throwable error) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        String routeId = exchange.getAttributeOrDefault("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRoute", UNKNOWN_ROUTE).toString();
        Integer statusCode = exchange.getResponse().getStatusCode() == null
                ? null
                : exchange.getResponse().getStatusCode().value();
        if (error == null) {
            log.info("event=GATEWAY_REQUEST_END route={} status={} durationMs={}", routeId, statusCode, durationMs);
            return;
        }
        log.warn("event=GATEWAY_REQUEST_ERROR route={} status={} durationMs={} errorType={} message={}",
                routeId,
                statusCode,
                durationMs,
                error.getClass().getSimpleName(),
                error.getMessage(),
                error);
    }

    /**
     * 完成 safe User Agent 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：网关层；输入来源、输出结构和异常语义由 GatewayTraceIdFilter 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param userAgent user Agent 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String safeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }
}
