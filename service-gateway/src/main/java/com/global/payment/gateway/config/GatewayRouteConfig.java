package com.global.payment.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator paymentRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("service-openapi", route -> route.path("/openapi/**").uri("lb://service-openapi"))
                .route("merchant-payment-api", route -> route.path("/payment/**").uri("lb://service-openapi"))
                .route("merchant-payout-api", route -> route.path("/payout/**").uri("lb://service-openapi"))
                .route("service-channel", route -> route.path("/channel/**").uri("lb://service-channel"))
                .build();
    }
}
