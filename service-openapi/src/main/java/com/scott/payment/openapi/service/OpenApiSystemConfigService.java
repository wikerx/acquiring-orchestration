package com.scott.payment.openapi.service;

/**
 * OpenAPI 运行参数读取服务。
 */
public interface OpenApiSystemConfigService {

    /**
     * 读取已启用且未删除的系统参数值。
     *
     * @param configKey 参数键名
     * @return 参数值
     */
    String requiredEnabledValue(String configKey);
}
