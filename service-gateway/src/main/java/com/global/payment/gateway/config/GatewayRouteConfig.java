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
                .route("service-payment", route -> route.path("/payment/**").uri("lb://service-payment"))
                .route("service-payout", route -> route.path("/payout/**").uri("lb://service-payout"))
                .route("service-channel", route -> route.path("/channel/**").uri("lb://service-channel"))
                .build();
    }
}

