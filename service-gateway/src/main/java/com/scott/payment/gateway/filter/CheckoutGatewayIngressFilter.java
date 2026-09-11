package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.gateway.config.GatewayIngressProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutGatewayIngressFilter
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 收银台网关入口过滤器，清除外部伪造头并为下游业务入口签发短时 HMAC 凭证。
 * @status : create
 */
@Component
public class CheckoutGatewayIngressFilter implements GlobalFilter, Ordered {

    private final GatewayIngressProperties properties;
    private final LongSupplier currentTimeMillis;
    private final Supplier<String> nonceSupplier;

    /**
     * 创建生产环境收银台入口签名过滤器。
     *
     * @param properties 外部注入的 Gateway 入口密钥
     */
    @Autowired
    public CheckoutGatewayIngressFilter(GatewayIngressProperties properties) {
        this(properties, System::currentTimeMillis, () -> UUID.randomUUID().toString().replace("-", ""));
    }

    CheckoutGatewayIngressFilter(GatewayIngressProperties properties,
                                 LongSupplier currentTimeMillis,
                                 Supplier<String> nonceSupplier) {
        this.properties = properties;
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
                });
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (!GatewayIngressSignature.isProtectedCheckoutPath(rawPath)) {
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

    private byte[] readAndRelease(DataBuffer buffer) {
        try {
            byte[] body = new byte[buffer.readableByteCount()];
            buffer.read(body);
            return body;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

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
        String clientIp = exchange.getRequest().getHeaders().getFirst(GatewayIngressSignature.HEADER_CLIENT_IP);
        String signature = GatewayIngressSignature.sign(
                exchange.getRequest().getMethod().name(), requestTarget, timestamp, nonce,
                bodySha256, clientIp, properties.getSecret());
        requestBuilder.headers(headers -> {
            headers.set(GatewayIngressSignature.HEADER_CALLER, GatewayIngressSignature.CALLER_SERVICE_GATEWAY);
            headers.set(GatewayIngressSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
            headers.set(GatewayIngressSignature.HEADER_NONCE, nonce);
            headers.set(GatewayIngressSignature.HEADER_BODY_SHA256, bodySha256);
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

    private Mono<Void> writeUnavailable(ServerWebExchange exchange) {
        byte[] body = ("{\"code\":\"" + ApiResultEnum.NETWORK_BUSY.getCode()
                + "\",\"message\":\"checkout gateway ingress is unavailable\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> writePayloadTooLarge(ServerWebExchange exchange) {
        byte[] body = ("{\"code\":\"" + ApiResultEnum.PARAM_INVALID.getCode()
                + "\",\"message\":\"request body is too large\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private ServerHttpRequest replayableRequest(ServerWebExchange exchange,
                                                ServerHttpRequest request,
                                                byte[] body) {
        return new ServerHttpRequestDecorator(request) {
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
