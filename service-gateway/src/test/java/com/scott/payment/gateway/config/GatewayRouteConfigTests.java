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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证公网入口只由 service-gateway 的明确白名单路由转发。
 */
class GatewayRouteConfigTests {

    /**
     * 商户建单、付款人 API、公开配置和健康入口必须分别命中明确路由。
     */
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

    @Test
    void shouldRouteIpAndCardBinQueriesToOpenApi() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PathRoutePredicateFactory.class);
            context.refresh();
            RouteLocator locator = new GatewayRouteConfig().openApiRoutes(new RouteLocatorBuilder(context));
            Map<String, Route> routes = locator.getRoutes().collectList().block().stream()
                    .collect(Collectors.toMap(Route::getId, Function.identity()));

            Route ipRoute = routes.get("merchant-ip-openapi");
            Route cardBinRoute = routes.get("merchant-card-bin-openapi");
            assertRouteMatches(ipRoute, "/api/rest/ip/v1/query");
            assertRouteMatches(cardBinRoute, "/api/rest/card-bin/v1/query");
            assertEquals("lb://service-openapi", ipRoute.getUri().toString());
            assertEquals("lb://service-openapi", cardBinRoute.getUri().toString());
        }
    }

    /**
     * OpenAPI 支持路由只开放健康检查，历史维护入口不得再命中下游服务。
     */
    @Test
    void shouldRouteOnlyOpenApiHealthSupportPath() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PathRoutePredicateFactory.class);
            context.refresh();
            RouteLocator locator = new GatewayRouteConfig().openApiRoutes(new RouteLocatorBuilder(context));
            Map<String, Route> routes = locator.getRoutes().collectList().block().stream()
                    .collect(Collectors.toMap(Route::getId, Function.identity()));

            Route supportRoute = routes.get("openapi-support");
            assertRouteMatches(supportRoute, "/openapi/health");
            assertRouteDoesNotMatch(supportRoute, "/openapi/v1/merchant-notifies/retry");
            assertRouteDoesNotMatch(supportRoute, "/openapi/health/details");
            assertEquals("lb://service-openapi", supportRoute.getUri().toString());
        }
    }

    /**
     * 未命中的商户 OpenAPI 路径必须进入专用 V1.3 兜底，不得落入内部系统旧码响应。
     */
    @Test
    void shouldRouteUnmatchedOpenApiPathToV13Fallback() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PathRoutePredicateFactory.class);
            context.refresh();
            RouteLocator locator = new GatewayRouteConfig().openApiRoutes(new RouteLocatorBuilder(context));
            Map<String, Route> routes = locator.getRoutes().collectList().block().stream()
                    .collect(Collectors.toMap(Route::getId, Function.identity()));

            Route fallbackRoute = routes.get("openapi-unmatched-path");
            assertRouteMatches(fallbackRoute, "/api/rest/unknown/v9/action");
            assertEquals("forward:/gateway/fallback/openapi-not-found", fallbackRoute.getUri().toString());
        }
    }

    private void assertRouteMatches(Route route, String path) {
        assertNotNull(route);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        assertTrue(Mono.from(route.getPredicate().apply(exchange)).block());
    }

    private void assertRouteDoesNotMatch(Route route, String path) {
        assertNotNull(route);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        assertFalse(Mono.from(route.getPredicate().apply(exchange)).block());
    }
}
