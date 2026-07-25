package com.scott.payment.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayRouteConfig
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 网关路由配置，统一声明外部请求可以进入系统的路径规则
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayRouteConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Gateway Route 配置，位于 service-gateway 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(GatewayClientIpProperties.class)
public class GatewayRouteConfig {

    /**
     * OpenAPI 服务注册名，网关通过 Nacos 服务发现转发到 service-openapi 实例。
     */
    private static final String SERVICE_OPENAPI_URI = "lb://service-openapi";

    /**
     * 管理后台服务注册名，前端管理端通过 gateway 统一访问 service-admin。
     */
    private static final String SERVICE_ADMIN_URI = "lb://service-admin";

    /**
     * 商户后台服务注册名，前端商户端通过 gateway 统一访问 service-merchant。
     */
    private static final String SERVICE_MERCHANT_URI = "lb://service-merchant";

    /**
     * 收银台服务注册名，付款人收银台页面通过 gateway 读取公开展示配置。
     */
    private static final String SERVICE_CHECKOUT_URI = "lb://service-checkout";

    /**
     * 商户收单 API 路径，形如 /api/rest/payment/v1/authorization。
     */
    private static final String PAYMENT_OPENAPI_PATH = "/api/rest/payment/**";

    /**
     * 商户代付 API 路径，形如 /api/rest/payout/v1/create。
     */
    private static final String PAYOUT_OPENAPI_PATH = "/api/rest/payout/**";

    /**
     * ISO 基础资料 API 路径，形如 /api/rest/iso/v1/countries/query。
     */
    private static final String ISO_OPENAPI_PATH = "/api/rest/iso/**";

    /**
     * 渠道回调路径，当前由 service-openapi 统一承接后再转发到支付或代付服务。
     */
    private static final String CHANNEL_CALLBACK_PATH = "/channel/**";

    /**
     * OpenAPI 运维或内部回调路径，例如健康检查、商户通知重试。
     */
    private static final String OPENAPI_SUPPORT_PATH = "/openapi/**";

    /**
     * 管理后台内部 API 路径，形如 /admin/auth/login、/admin/system/configs/search。
     */
    private static final String ADMIN_API_PATH = "/admin/**";

    /**
     * 商户后台内部 API 路径，形如 /merchant/auth/login、/merchant/system/roles。
     */
    private static final String MERCHANT_API_PATH = "/merchant/**";

    /**
     * 收银台公开 API 路径，形如 /checkout/config/countries。
     */
    private static final String CHECKOUT_API_PATH = "/checkout/**";

    /**
     * 网关本地兜底路径，用于输出统一 JSON 错误响应。
     */
    private static final String GATEWAY_NOT_FOUND_PATH = "/gateway/fallback/not-found";

    /**
     * 网关本地兜底路径匹配表达式，避免兜底路由再次转发自身导致循环。
     */
    private static final String GATEWAY_FALLBACK_PATH = "/gateway/fallback/**";

    /**
     * 任意路径匹配表达式，作为最后一条兜底路由使用。
     */
    private static final String ANY_PATH = "/**";

    /**
     * 构建网关显式路由。
     * <p>
     * 规则说明：
     * 1. 网关只做接入层路径路由、基础响应头处理和跨域处理；
     * 2. JWT 详细验签、data 解密、参数校验统一下沉到 service-openapi；
     * 3. 数据库、Redis、MQ、Seata、分表配置不在 gateway 加载，避免接入层耦合业务基础设施。
     * 4. 未命中白名单路径的请求直接在 gateway 返回统一 JSON，避免暴露默认错误页。
     *
     * @param builder Spring Cloud Gateway 路由构建器
     * @return 网关路由集合
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param builder 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    public RouteLocator openApiRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("merchant-payment-openapi", route -> route.path(PAYMENT_OPENAPI_PATH).uri(SERVICE_OPENAPI_URI))
                .route("merchant-payout-openapi", route -> route.path(PAYOUT_OPENAPI_PATH).uri(SERVICE_OPENAPI_URI))
                .route("merchant-iso-openapi", route -> route.path(ISO_OPENAPI_PATH).uri(SERVICE_OPENAPI_URI))
                .route("channel-callback-openapi", route -> route.path(CHANNEL_CALLBACK_PATH).uri(SERVICE_OPENAPI_URI))
                .route("openapi-support", route -> route.path(OPENAPI_SUPPORT_PATH).uri(SERVICE_OPENAPI_URI))
                .route("admin-api", route -> route.path(ADMIN_API_PATH).uri(SERVICE_ADMIN_URI))
                .route("merchant-api", route -> route.path(MERCHANT_API_PATH).uri(SERVICE_MERCHANT_URI))
                .route("checkout-api", route -> route.path(CHECKOUT_API_PATH).uri(SERVICE_CHECKOUT_URI))
                .route("gateway-unmatched-path", route -> route.order(Ordered.LOWEST_PRECEDENCE)
                        .path(ANY_PATH)
                        .and()
                        .not(not -> not.path(GATEWAY_FALLBACK_PATH))
                        .uri("forward:" + GATEWAY_NOT_FOUND_PATH))
                .build();
    }
}
