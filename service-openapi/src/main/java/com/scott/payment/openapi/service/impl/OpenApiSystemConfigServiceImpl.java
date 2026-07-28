package com.scott.payment.openapi.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.openapi.entity.SysConfigDO;
import com.scott.payment.openapi.mapper.SysConfigMapper;
import com.scott.payment.openapi.service.OpenApiSystemConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * OpenAPI 运行参数读取默认实现。
 */
@Service
public class OpenApiSystemConfigServiceImpl implements OpenApiSystemConfigService {

    private static final long NOT_DELETED = 0L;
    private static final int ENABLED = 1;

    private final SysConfigMapper sysConfigMapper;

    public OpenApiSystemConfigServiceImpl(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public String requiredEnabledValue(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "configKey can not be blank");
        }
        SysConfigDO row = sysConfigMapper.selectOne(Wrappers.<SysConfigDO>lambdaQuery()
                .eq(SysConfigDO::getConfigKey, configKey.trim())
                .eq(SysConfigDO::getStatus, ENABLED)
                .eq(SysConfigDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null || !StringUtils.hasText(row.getConfigValue())) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR,
                    "system config is not enabled or empty:" + configKey);
        }
        return row.getConfigValue().trim();
    }
}
