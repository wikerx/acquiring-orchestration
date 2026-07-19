package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.entity.SysConfigDO;
import com.scott.payment.merchant.mapper.SysConfigMapper;
import com.scott.payment.merchant.service.MerchantConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantConfigServiceImpl
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户系统只读参数服务实现，位于 service-merchant 服务实现层，只读取 Admin 参数管理维护的启用配置。
 * @status : create
 */
@Service
public class MerchantConfigServiceImpl implements MerchantConfigService {

    /**
     * 未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 启用状态。
     */
    private static final int ENABLED = 1;

    /**
     * 系统参数 Mapper。
     */
    private final SysConfigMapper sysConfigMapper;

    /**
     * 创建商户端只读参数服务。
     *
     * @param sysConfigMapper 系统参数 Mapper
     */
    public MerchantConfigServiceImpl(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    /**
     * 查询启用且未删除的系统参数值。
     *
     * @param configKey 参数键名
     * @return 参数值；不存在、停用或空值时返回空
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public Optional<String> enabledConfigValue(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return Optional.empty();
        }
        SysConfigDO row = sysConfigMapper.selectOne(Wrappers.<SysConfigDO>lambdaQuery()
                .select(SysConfigDO::getConfigValue)
                .eq(SysConfigDO::getConfigKey, configKey.trim())
                .eq(SysConfigDO::getStatus, ENABLED)
                .eq(SysConfigDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null || !StringUtils.hasText(row.getConfigValue())) {
            return Optional.empty();
        }
        return Optional.of(row.getConfigValue().trim());
    }
}
