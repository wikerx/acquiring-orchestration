package com.scott.payment.openapi.service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiSystemConfigService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : OpenAPI 运行参数读取服务。
 * @status : create
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
