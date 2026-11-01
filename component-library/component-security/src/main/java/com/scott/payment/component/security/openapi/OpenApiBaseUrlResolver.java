package com.scott.payment.component.security.openapi;

/**
 * 商户 OpenAPI 接入材料地址解析器，位于安全组件层，只定义读取契约，不依赖管理系统参数实现。
 */
@FunctionalInterface
public interface OpenApiBaseUrlResolver {

    /**
     * 解析商户调用 OpenAPI 的外部基础地址。
     *
     * @return 商户 OpenAPI base URL
     */
    String resolve();
}
