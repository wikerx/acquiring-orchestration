package com.scott.payment.component.security.openapi;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiBaseUrlResolver
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 接入材料地址解析器，位于安全组件层，只定义读取契约，不依赖管理系统参数实现。
 * @status : create
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
