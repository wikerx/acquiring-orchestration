package com.scott.payment.gateway.config;

import com.scott.payment.gateway.GatewayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayHealthEndpointTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 service-gateway 暴露可供部署探针使用的本地健康端点。
 * @status : create
 */
@ActiveProfiles("gateway-health-test")
@SpringBootTest(
        classes = GatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "acquiring.gateway-ingress.secret=0123456789abcdef0123456789abcdef",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        })
class GatewayHealthEndpointTests {

    @Autowired
    private WebTestClient webTestClient;

    /** 健康探针必须返回 Actuator 状态，不能被网关未命中路由转换为业务 F404。 */
    @Test
    void shouldExposeActuatorHealthEndpoint() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
