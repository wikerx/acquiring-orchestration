package com.scott.payment.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayRouteConfig
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 网关路由配置
 * @status : create
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator paymentRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("service-openapi", route -> route.path("/openapi/**").uri("lb://service-openapi"))
                .route("merchant-payment-api", route -> route.path("/payment/**").uri("lb://service-openapi"))
                .route("merchant-payout-api", route -> route.path("/payout/**").uri("lb://service-openapi"))
                .route("channel-callback-api", route -> route.path("/channel/**").uri("lb://service-openapi"))
                .build();
    }
}
