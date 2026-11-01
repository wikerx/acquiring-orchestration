package com.scott.payment.gateway.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiMessageLocale;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.openapi.result.OpenApiResultMessageCatalog;
import com.scott.payment.component.core.trace.TraceContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 在 WebFlux Gateway 边界写出 OpenAPI V1.3 错误外壳，统一 HTTP 状态、traceId、缓存策略和 JSON 序列化，不处理业务异常映射。
 */
@Component
public class GatewayOpenApiErrorWriter {

    private final ObjectMapper objectMapper;

    /**
     * 创建 Gateway OpenAPI 错误写出器。
     *
     * @param objectMapper Spring WebFlux 使用的 JSON 序列化器
     */
    public GatewayOpenApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写出不含业务 data 的 V1.3 英文错误响应。
     * <p>
     * traceId 优先沿用 GatewayTraceIdFilter 已校验的请求头；缺失或非法时生成新值，确保商户收到的
     * Header 与 Gateway 错误日志可直接关联。商户 OpenAPI 使用商户英文模板，Checkout 浏览器入口使用
     * 付款人英文模板；该方法不读取或记录请求体、认证头和异常原文。
     * </p>
     *
     * @param exchange 当前 WebFlux 请求交换对象
     * @param status HTTP 传输状态
     * @param resultCode 已登记的 OpenAPI V1.3 返回码
     * @return 响应体写出完成信号
     */
    public Mono<Void> write(ServerWebExchange exchange,
                            HttpStatusCode status,
                            OpenApiResultCode resultCode) {
        byte[] responseBody;
        try {
            OpenApiMessageAudience audience = resolveAudience(exchange);
            responseBody = objectMapper.writeValueAsBytes(CommonResult.error(
                    resultCode.getCode(),
                    OpenApiResultMessageCatalog.message(resultCode, audience, OpenApiMessageLocale.EN_US)));
        } catch (JsonProcessingException exception) {
            return Mono.error(new IllegalStateException("Gateway OpenAPI error response serialization failed", exception));
        }
        String responseTraceId = exchange.getResponse().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER);
        String traceId = TraceContext.resolveOrCreate(responseTraceId == null
                ? exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER)
                : responseTraceId);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl(CacheControl.noStore());
        exchange.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(responseBody);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Checkout 浏览器路径面向付款人，其余 Gateway OpenAPI 错误默认面向商户。
     */
    private OpenApiMessageAudience resolveAudience(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getRawPath();
        return path != null && (path.equals("/checkout") || path.startsWith("/checkout/"))
                ? OpenApiMessageAudience.PAYER
                : OpenApiMessageAudience.MERCHANT;
    }
}
