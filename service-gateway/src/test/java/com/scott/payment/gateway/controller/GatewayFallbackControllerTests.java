package com.scott.payment.gateway.controller;

import com.scott.payment.component.core.model.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Gateway 未命中路由时按商户 OpenAPI 与内部系统命名空间返回各自的错误契约。
 */
class GatewayFallbackControllerTests {

    /**
     * 商户 OpenAPI 未命中路由必须返回 HTTP 404 与 V1.3 A3002。
     */
    @Test
    void shouldReturnV13CodeForUnmatchedOpenApiPath() {
        ResponseEntity<CommonResult<Void>> response = new GatewayFallbackController().openApiNotFound();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A3002");
        assertThat(response.getBody().getMessage())
                .isEqualTo("The API endpoint was not found. Check the URL and API version.");
    }
}
