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
    /**
     * 完成 filter 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param exchange exchange 输入值，含义由调用方法名称和所属业务对象限定
     * @param chain chain 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 get Order 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    /**
     * 完成 log Finish 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param exchange exchange 输入值，含义由调用方法名称和所属业务对象限定
     * @param startNanos start Nanos 输入值，含义由调用方法名称和所属业务对象限定
     * @param error error 输入值，含义由调用方法名称和所属业务对象限定
     */
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
     * 完成 safe User Agent 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param userAgent user Agent 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String safeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }
}
