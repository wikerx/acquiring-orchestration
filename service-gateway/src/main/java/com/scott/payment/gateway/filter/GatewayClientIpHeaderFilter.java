package com.scott.payment.gateway.filter;

import com.scott.payment.gateway.config.GatewayClientIpProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayClientIpHeaderFilter
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 网关客户端 IP 透传过滤器，位于 service-gateway 过滤层，统一覆盖可信 X-Gateway-Client-Ip 供 OpenAPI 白名单校验使用。
 * @status : create
 */
@Component
public class GatewayClientIpHeaderFilter implements GlobalFilter, Ordered {

    /**
     * Gateway 写给下游服务的可信客户端 IP 请求头。
     */
    public static final String HEADER_GATEWAY_CLIENT_IP = "X-Gateway-Client-Ip";

    /**
     * 标准代理链客户端地址请求头。只有部署配置明确声明上游代理可信时才读取该头。
     */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 客户端地址解析配置，决定是否接受上游代理提供的转发链。
     */
    private final GatewayClientIpProperties clientIpProperties;

    /**
     * 创建网关客户端 IP 透传过滤器。
     *
     * @param clientIpProperties 客户端 IP 透传配置
     */
    public GatewayClientIpHeaderFilter(GatewayClientIpProperties clientIpProperties) {
        this.clientIpProperties = clientIpProperties;
    }

    /**
     * 覆盖可信客户端 IP 请求头并继续转发。
     *
     * @param exchange 当前网关请求上下文
     * @param chain    后续过滤器链
     * @return 异步处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange.getRequest());
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(HEADER_GATEWAY_CLIENT_IP));
        if (StringUtils.hasText(clientIp)) {
            requestBuilder.header(HEADER_GATEWAY_CLIENT_IP, clientIp);
        }
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    /**
     * 让该过滤器尽早执行，避免后续路由过滤器拿到外部伪造的同名头。
     *
     * @return 过滤器顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    /**
     * 按信任配置解析下游可使用的客户端地址。
     * <p>
     * 仅在可信代理模式下读取 {@code X-Forwarded-For}；未开启、头缺失或内容为空时回退到
     * TCP 远端地址，避免公网调用方通过伪造请求头绕过 OpenAPI IP 白名单。
     * </p>
     *
     * @param request 当前网关请求
     * @return 已解析的客户端 IP；连接没有可用远端地址时返回 {@code null}
     */
    private String resolveClientIp(ServerHttpRequest request) {
        if (clientIpProperties.isTrustForwardedHeader()) {
            String forwardedFor = request.getHeaders().getFirst(HEADER_X_FORWARDED_FOR);
            String forwardedClientIp = firstForwardedIp(forwardedFor);
            if (StringUtils.hasText(forwardedClientIp)) {
                return forwardedClientIp;
            }
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 从标准逗号分隔的代理链中提取最左侧非空地址。
     * <p>
     * 该地址代表可信边界配置下的原始客户端；本方法只做格式拆分，代理可信性由调用方在读取
     * 请求头前完成判定。
     * </p>
     *
     * @param forwardedFor 代理链原始值
     * @return 首个非空地址；请求头为空或不含有效片段时返回 {@code null}
     */
    private String firstForwardedIp(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }
        String[] segments = forwardedFor.split(",");
        for (String segment : segments) {
            if (StringUtils.hasText(segment)) {
                return segment.trim();
            }
        }
        return null;
    }
}
