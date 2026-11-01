package com.scott.payment.gateway.handler;

import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.gateway.support.GatewayOpenApiErrorWriter;
import com.scott.payment.gateway.support.GatewayRequestIdSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 将商户 OpenAPI 和付款人 Checkout API 在 Gateway 路由、连接及响应阶段的未处理异常投影为英文 F2000。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayOpenApiExceptionHandler implements WebExceptionHandler {

    /**
     * {@code OPENAPI_PATH_PREFIX}，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String OPENAPI_PATH_PREFIX = "/api/rest/";
    /**
     * {@code CHECKOUT_API_PATH_PREFIX}，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CHECKOUT_API_PATH_PREFIX = "/checkout/api/";
    /**
     * {@code UNKNOWN}常量，统一 {@code GatewayOpenApiExceptionHandler} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String UNKNOWN = "unknown";

    private final GatewayOpenApiErrorWriter errorWriter;

    /**
     * 创建商户 OpenAPI Gateway 异常处理器。
     *
     * @param errorWriter V1.3 错误响应写出器
     */
    public GatewayOpenApiExceptionHandler(GatewayOpenApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    /**
     * 仅处理 `/api/rest/**`、`/checkout/api/**` 且尚未提交响应的异常。
     * <p>
     * HTTP 状态优先沿用异常或响应中已有状态；连接失败映射 503、超时映射 504，其他未分类异常映射 500。
     * 响应体固定使用安全公开的 F2000，不透传异常 message、服务地址、路由细节或上游响应体。
     * </p>
     *
     * @param exchange 当前 WebFlux 请求交换对象
     * @param exception Gateway 链路异常
     * @return 已处理时写出 V1.3 响应；非 OpenAPI 或响应已提交时继续传播原异常
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (!isOpenApi(exchange) || exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }
        HttpStatusCode status = resolveStatus(exchange, exception);
        String traceId = TraceContext.resolveOrCreate(
                exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER));
        String requestId = GatewayRequestIdSupport.resolveForLogging(exchange.getRequest().getHeaders());
        exchange.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        log.warn("event: GATEWAY_OPENAPI_ERROR_RESPONSE traceId: {} requestId: {} path: {} routeId: {} targetService: {} status: {} errorType: {} platformCode: {}",
                traceId,
                requestId,
                exchange.getRequest().getURI().getRawPath(),
                routeId(exchange),
                targetService(exchange),
                status.value(),
                exception.getClass().getSimpleName(),
                OpenApiResultCode.SYSTEM_ERROR.getCode());
        return errorWriter.write(exchange, status, OpenApiResultCode.SYSTEM_ERROR);
    }

    private static boolean isOpenApi(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        return path.equals("/api/rest")
                || path.startsWith(OPENAPI_PATH_PREFIX)
                || path.equals("/checkout/api")
                || path.startsWith(CHECKOUT_API_PATH_PREFIX);
    }

    private static HttpStatusCode resolveStatus(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().getStatusCode() != null) {
            return exchange.getResponse().getStatusCode();
        }
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ResponseStatusException statusException) {
                return statusException.getStatusCode();
            }
            if (current instanceof TimeoutException || current instanceof SocketTimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (current instanceof ConnectException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String routeId(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return route == null ? UNKNOWN : route.getId();
    }

    private static String targetService(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null || route.getUri() == null) {
            return UNKNOWN;
        }
        if (StringUtils.hasText(route.getUri().getHost())) {
            return route.getUri().getHost();
        }
        return route.getUri().getSchemeSpecificPart();
    }
}
