package com.scott.payment.openapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiApplicationTests
 * @date : 2026-05-29 00:00
 * @email : scott_x@163.com
 * @description : service-openapi 测试入口，后续开放接口、鉴权、解密和版本降级测试从这里扩展
 * @status : create
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
