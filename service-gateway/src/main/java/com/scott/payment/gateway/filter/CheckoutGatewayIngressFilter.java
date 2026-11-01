package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.gateway.config.GatewayIngressProperties;
import com.scott.payment.gateway.support.GatewayOpenApiErrorWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 收银台网关入口过滤器，清除外部伪造头并为下游业务入口签发短时 HMAC 凭证。
 */
@Component
public class CheckoutGatewayIngressFilter implements GlobalFilter, Ordered {

    private final GatewayIngressProperties properties;
    private final GatewayOpenApiErrorWriter errorWriter;
    private final LongSupplier currentTimeMillis;
    private final Supplier<String> nonceSupplier;

    /**
     * 创建生产环境收银台入口签名过滤器。
     *
     * @param properties 外部注入的 Gateway 入口密钥
     * @param errorWriter OpenAPI V1.3 错误响应写出器
     */
    @Autowired
    public CheckoutGatewayIngressFilter(GatewayIngressProperties properties,
                                        GatewayOpenApiErrorWriter errorWriter) {
        this(properties, errorWriter, System::currentTimeMillis,
                () -> UUID.randomUUID().toString().replace("-", ""));
    }

    /**
     * 创建可注入时钟和 nonce 生成器的收银台入口签名过滤器，供确定性测试使用。
     *
     * @param properties 网关入口签名配置
     * @param errorWriter OpenAPI 错误响应写出器
     * @param currentTimeMillis 当前时间提供器
     * @param nonceSupplier 单次请求随机数提供器
     */
    CheckoutGatewayIngressFilter(GatewayIngressProperties properties,
                                 GatewayOpenApiErrorWriter errorWriter,
                                 LongSupplier currentTimeMillis,
                                 Supplier<String> nonceSupplier) {
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.currentTimeMillis = currentTimeMillis;
        this.nonceSupplier = nonceSupplier;
    }

    /**
     * 覆盖客户端伪造头，并只为显式收银台路由生成可信入口签名。
     *
     * @param exchange 当前 Gateway 请求上下文
     * @param chain 后续路由链
     * @return 异步处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(GatewayIngressSignature.HEADER_CALLER);
                    headers.remove(GatewayIngressSignature.HEADER_TIMESTAMP);
                    headers.remove(GatewayIngressSignature.HEADER_NONCE);
                    headers.remove(GatewayIngressSignature.HEADER_SIGNATURE);
                    headers.remove(GatewayIngressSignature.HEADER_BODY_SHA256);
                    headers.remove(GatewayIngressSignature.HEADER_CLIENT_IP);
                });
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (!GatewayIngressSignature.isProtectedGatewayPath(rawPath)) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }
        if (!GatewayIngressSignature.isConfiguredSecret(properties.getSecret())) {
            return writeUnavailable(exchange);
        }

        int maxBodyBytes = Math.max(properties.getMaxRequestBodyBytes(), 1);
        long declaredLength = exchange.getRequest().getHeaders().getContentLength();
        if (declaredLength > maxBodyBytes) {
            return writePayloadTooLarge(exchange);
        }
        return DataBufferUtils.join(exchange.getRequest().getBody(), maxBodyBytes)
                .map(this::readAndRelease)
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> signAndForward(exchange, chain, requestBuilder, rawPath, body))
                .onErrorResume(DataBufferLimitException.class, exception -> writePayloadTooLarge(exchange));
    }

    /**
     * 复制请求缓冲区内容并始终释放 Netty 引用计数。
     *
     * @param buffer 待读取的数据缓冲区
     * @return 可重复读取的请求正文字节
     */
    private byte[] readAndRelease(DataBuffer buffer) {
        try {
            byte[] body = new byte[buffer.readableByteCount()];
            buffer.read(body);
            return body;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    /**
     * 基于原始请求目标、正文摘要和可信客户端 IP 生成签名后继续转发。
     *
     * @param exchange 当前 Gateway 请求上下文
     * @param chain 后续路由链
     * @param requestBuilder 已清除外部伪造签名头的请求构造器
     * @param rawPath 未解码请求路径
     * @param body 请求正文字节
     * @return 下游异步处理结果
     */
    private Mono<Void> signAndForward(ServerWebExchange exchange,
                                      GatewayFilterChain chain,
                                      ServerHttpRequest.Builder requestBuilder,
                                      String rawPath,
                                      byte[] body) {
        long timestamp = currentTimeMillis.getAsLong();
        String nonce = nonceSupplier.get();
        String requestTarget = GatewayIngressSignature.requestTarget(
                rawPath, exchange.getRequest().getURI().getRawQuery());
        String bodySha256 = GatewayIngressSignature.payloadSha256(body);
        Object trustedClientIp = exchange.getAttribute(GatewayClientIpHeaderFilter.GATEWAY_CLIENT_IP_ATTRIBUTE);
        String clientIp = trustedClientIp instanceof String value ? value : null;
        String signature = GatewayIngressSignature.sign(
                exchange.getRequest().getMethod().name(), requestTarget, timestamp, nonce,
                bodySha256, clientIp, properties.getSecret());
        requestBuilder.headers(headers -> {
            headers.set(GatewayIngressSignature.HEADER_CALLER, GatewayIngressSignature.CALLER_SERVICE_GATEWAY);
            headers.set(GatewayIngressSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
            headers.set(GatewayIngressSignature.HEADER_NONCE, nonce);
            headers.set(GatewayIngressSignature.HEADER_BODY_SHA256, bodySha256);
            if (StringUtils.hasText(clientIp)) {
                headers.set(GatewayIngressSignature.HEADER_CLIENT_IP, clientIp);
            }
            headers.set(GatewayIngressSignature.HEADER_SIGNATURE, signature);
        });
        ServerHttpRequest signedRequest = replayableRequest(exchange, requestBuilder.build(), body);
        return chain.filter(exchange.mutate().request(signedRequest).build());
    }

    /**
     * 在 traceId 和客户端 IP 处理后、路由转发前执行。
     *
     * @return Gateway 全局过滤器顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 25;
    }

    /**
     * 在入口签名密钥未配置时返回失败关闭响应。
     *
     * @param exchange 当前 Gateway 请求上下文
     * @return 错误响应写出结果
     */
    private Mono<Void> writeUnavailable(ServerWebExchange exchange) {
        return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, OpenApiResultCode.SYSTEM_ERROR);
    }

    /**
     * 在声明或实际正文超过上限时返回统一的 413 响应。
     *
     * @param exchange 当前 Gateway 请求上下文
     * @return 错误响应写出结果
     */
    private Mono<Void> writePayloadTooLarge(ServerWebExchange exchange) {
        return errorWriter.write(exchange, HttpStatus.PAYLOAD_TOO_LARGE, OpenApiResultCode.REQUEST_TOO_LARGE);
    }

    /**
     * 使用缓存字节重建可供下游再次订阅的请求正文。
     *
     * @param exchange 当前 Gateway 请求上下文
     * @param request 已写入可信签名头的请求
     * @param body 已缓存请求正文
     * @return 可重复读取正文的请求装饰器
     */
    private ServerHttpRequest replayableRequest(ServerWebExchange exchange,
                                                ServerHttpRequest request,
                                                byte[] body) {
        return new ServerHttpRequestDecorator(request) {
            /**
             * 为每次订阅创建新的 DataBuffer，避免复用已释放缓冲区。
             *
             * @return 当前缓存正文的数据流
             */
            @Override
            public Flux<DataBuffer> getBody() {
                if (body.length == 0) {
                    return Flux.empty();
                }
                return Flux.defer(() -> Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
            }
        };
    }
}
