package com.scott.payment.gateway.filter;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.gateway.config.GatewayClientIpProperties;
import com.scott.payment.gateway.support.TrustedProxyMatcher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

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

    /** Gateway 进程内保存可信客户端 IP 的 exchange attribute，外部请求无法注入。 */
    public static final String GATEWAY_CLIENT_IP_ATTRIBUTE =
            GatewayClientIpHeaderFilter.class.getName() + ".CLIENT_IP";

    /**
     * Gateway 写给下游服务的可信客户端 IP 请求头。
     */
    public static final String HEADER_GATEWAY_CLIENT_IP = GatewayIngressSignature.HEADER_CLIENT_IP;

    /**
     * 标准代理链客户端地址请求头。只有部署配置明确声明上游代理可信时才读取该头。
     */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 客户端地址解析配置，决定是否接受上游代理提供的转发链。
     */
    private final GatewayClientIpProperties clientIpProperties;

    /** 仅接受配置网段内的直接上游代理，并用于从右向左剥离可信代理链。 */
    private final TrustedProxyMatcher trustedProxyMatcher;

    /**
     * 创建网关客户端 IP 透传过滤器。
     *
     * @param clientIpProperties 客户端 IP 透传配置
     */
    public GatewayClientIpHeaderFilter(GatewayClientIpProperties clientIpProperties) {
        this.clientIpProperties = clientIpProperties;
        this.trustedProxyMatcher = new TrustedProxyMatcher(clientIpProperties.getTrustedProxyCidrs());
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
            exchange.getAttributes().put(GATEWAY_CLIENT_IP_ATTRIBUTE, clientIp);
        } else {
            exchange.getAttributes().remove(GATEWAY_CLIENT_IP_ATTRIBUTE);
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
        String remoteIp = remoteIp(request);
        if (clientIpProperties.isTrustForwardedHeader()
                && remoteIp != null
                && trustedProxyMatcher.matches(remoteIp)) {
            List<String> forwardedValues = request.getHeaders().get(HEADER_X_FORWARDED_FOR);
            String forwardedFor = forwardedValues != null && forwardedValues.size() == 1
                    ? forwardedValues.get(0)
                    : null;
            String forwardedClientIp = firstUntrustedForwardedIp(forwardedFor);
            if (StringUtils.hasText(forwardedClientIp)) {
                return forwardedClientIp;
            }
        }
        return remoteIp;
    }

    private String remoteIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }
        return trustedProxyMatcher.normalize(remoteAddress.getAddress().getHostAddress());
    }

    /**
     * 从右向左剥离标准代理链中的可信节点，并返回第一个不可信地址。
     * <p>
     * 只有 TCP 直接上游已在可信 CIDR 中时才调用本方法。链内任一片段为空或不是精确 IP，
     * 整条转发链均作废并回退 TCP 地址，避免攻击者利用畸形或伪造头绕过白名单和 IP 限流。
     * 如果整条链均为可信代理，则返回最左侧地址。
     * </p>
     *
     * @param forwardedFor 代理链原始值
     * @return 第一个非可信地址；请求头无效时返回 {@code null}
     */
    private String firstUntrustedForwardedIp(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }
        String[] segments = forwardedFor.split(",");
        String leftmost = null;
        try {
            for (int index = segments.length - 1; index >= 0; index--) {
                if (!StringUtils.hasText(segments[index])) {
                    return null;
                }
                String normalized = trustedProxyMatcher.normalize(segments[index].trim());
                leftmost = normalized;
                if (!trustedProxyMatcher.matches(normalized)) {
                    return normalized;
                }
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return leftmost;
    }
}
