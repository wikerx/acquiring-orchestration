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
     * HEADER X FORWARDED FOR，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * client IP Properties 依赖，用于 Gateway Client IP Header Filter 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
