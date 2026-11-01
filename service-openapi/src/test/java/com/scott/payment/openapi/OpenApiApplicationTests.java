package com.scott.payment.openapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * service-openapi 测试入口，后续开放接口、鉴权、解密和版本降级测试从这里扩展
 */
class OpenApiApplicationTests {

    /**
     * 验证 OpenApi 服务启动类存在，避免测试入口缺失。
     */
    @Test
    void shouldKeepOpenApiApplicationEntry() {
        assertThat(OpenApiApplication.class).isNotNull();
    }
}
