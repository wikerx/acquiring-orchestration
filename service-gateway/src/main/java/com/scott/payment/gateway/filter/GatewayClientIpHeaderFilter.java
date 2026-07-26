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
     * HEADER X FORWARDED FOR 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * client Ip Properties 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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

    /**
     * 解析 resolve Client Ip 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 解析或查询得到的业务值
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
     * 完成 first Forwarded Ip 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param forwardedFor forwarded For 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
