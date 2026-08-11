package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.systemconfig.service.SystemConfigReadService;
import com.scott.payment.openapi.service.OpenApiSystemConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OpenAPI 运行参数读取默认实现。
 */
@Service
public class OpenApiSystemConfigServiceImpl implements OpenApiSystemConfigService {

    /** 跨服务统一系统参数读取服务。 */
    private final SystemConfigReadService systemConfigReadService;

    /**
     * 创建 OpenAPI 系统参数读取服务。
     *
     * @param systemConfigReadService 跨服务统一系统参数读取服务
     */
    public OpenApiSystemConfigServiceImpl(SystemConfigReadService systemConfigReadService) {
        this.systemConfigReadService = systemConfigReadService;
    }

    /**
     * 从只读数据源获取已启用且未删除的必需配置值。
     *
     * <p>配置键为空、记录不存在、配置停用或值为空时直接失败，避免收银台等安全相关流程
     * 在缺少明确平台配置时使用未经审核的默认地址继续执行。</p>
     *
     * @param configKey 系统参数键名
     * @return 去除首尾空白后的配置值
     * @throws ApiException 参数非法或必需配置不可用时抛出
     */
    @Override
    public String requiredEnabledValue(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "configKey can not be blank");
        }
        return systemConfigReadService.findEnabledValue(configKey)
                .orElseThrow(() -> new ApiException(
                        ApiResultEnum.INTERNAL_SERVER_ERROR,
                        "system config is not enabled or empty:" + configKey
                ));
    }
}
