package com.scott.payment.component.db.systemconfig.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.systemconfig.entity.SystemConfigDO;
import com.scott.payment.component.db.systemconfig.mapper.SystemConfigMapper;
import com.scott.payment.component.db.systemconfig.model.SystemConfigSnapshot;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemConfigCacheReader
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 系统参数缓存代理读取器，隔离 Cacheable 入口与强制主库回源入口
 * @status : create
 */
@Service
public class SystemConfigCacheReader {

    private static final long NOT_DELETED = 0L;

    private final SystemConfigMapper systemConfigMapper;

    /**
     * 创建系统参数缓存读取器。
     *
     * @param systemConfigMapper 公共系统参数 Mapper
     */
    public SystemConfigCacheReader(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    /**
     * 从统一永久缓存读取配置，未命中时查询主库。
     *
     * @param configKey 已规范化的全局唯一参数键名
     * @return 未删除配置快照；不存在时返回 null 且不缓存空值
     */
    @DS(DataSourceName.MASTER)
    @Cacheable(
            cacheNames = PaymentCacheNames.SYSTEM_CONFIG,
            key = "#p0",
            unless = "#result == null"
    )
    public SystemConfigSnapshot findCached(String configKey) {
        return load(configKey);
    }

    /**
     * pending 门禁存在或 Redis 状态未知时绕过缓存读取主库。
     *
     * @param configKey 已规范化的全局唯一参数键名
     * @return 当前主库中的未删除配置快照；不存在时返回 null
     */
    @DS(DataSourceName.MASTER)
    public SystemConfigSnapshot findFresh(String configKey) {
        return load(configKey);
    }

    private SystemConfigSnapshot load(String configKey) {
        SystemConfigDO row = systemConfigMapper.selectOne(
                Wrappers.<SystemConfigDO>lambdaQuery()
                        .eq(SystemConfigDO::getConfigKey, configKey)
                        .eq(SystemConfigDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        return row == null ? null : toSnapshot(row);
    }

    private SystemConfigSnapshot toSnapshot(SystemConfigDO row) {
        return new SystemConfigSnapshot(
                row.getId(),
                row.getConfigName(),
                row.getConfigKey(),
                row.getConfigValue(),
                row.getValueType(),
                row.getConfigGroup(),
                row.getSystemBuiltin(),
                row.getVisible(),
                row.getEncrypted(),
                row.getStatus(),
                row.getRemark(),
                row.getCreatedBy(),
                row.getUpdatedBy(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
