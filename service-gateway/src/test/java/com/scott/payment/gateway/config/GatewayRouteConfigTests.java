package com.scott.payment.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayRouteConfigTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证四类收银台公网路径都由 service-gateway 显式路由。
 * @status : create
 */
class GatewayRouteConfigTests {

    /** 商户建单、付款人 API、公开配置和健康入口必须分别命中明确路由。 */
    @Test
    void shouldRouteEveryCheckoutBackendIngress() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PathRoutePredicateFactory.class);
            context.refresh();
            RouteLocator locator = new GatewayRouteConfig().openApiRoutes(new RouteLocatorBuilder(context));
            Map<String, Route> routes = locator.getRoutes().collectList().block().stream()
                    .collect(Collectors.toMap(Route::getId, Function.identity()));

            assertRouteMatches(routes.get("merchant-checkout-openapi"), "/api/rest/checkout/v1/session");
            assertRouteMatches(routes.get("checkout-browser-api"), "/checkout/api/v1/payment/submit");
            assertRouteMatches(routes.get("checkout-config"), "/checkout/config/countries");
            assertRouteMatches(routes.get("checkout-config"), "/checkout/health");
        }
    }

    private void assertRouteMatches(Route route, String path) {
        assertNotNull(route);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        assertTrue(Mono.from(route.getPredicate().apply(exchange)).block());
    }
}
