package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.entity.SysConfigDO;
import com.scott.payment.merchant.mapper.SysConfigMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PlatformConfigCacheReader
 * @date : 2026-07-31 00:00
 * @email : scott_x@163.com
 * @description : 商户服务基础设施读取器，隔离平台公开配置的 Spring Cache 代理读取与 MASTER 强制回源路径
 * @status : create
 *
 * <p>数据库是配置事实源。缓存未命中和 pending 期间的强制回源均查询主库，避免数据库主从
 * 延迟把事务提交前的旧配置重新写入永久缓存。</p>
 */
@Service
public class PlatformConfigCacheReader {

    /**
     * 数据库逻辑未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 数据库启用状态值。
     */
    private static final int ENABLED = 1;

    /**
     * 系统参数只读 Mapper。
     */
    private final SysConfigMapper sysConfigMapper;

    /**
     * 创建平台公开配置缓存读取器。
     *
     * @param sysConfigMapper 系统参数只读 Mapper
     */
    public PlatformConfigCacheReader(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    /**
     * 正常状态下读取永久缓存，未命中时从主库加载非空配置值。
     *
     * @param configKey 已通过白名单校验并规范化的平台配置键
     * @return 启用且非空的配置值；记录不存在、停用或值为空时返回空
     */
    @DS(DataSourceName.MASTER)
    @Cacheable(
            cacheNames = PaymentCacheNames.PLATFORM_CONFIG,
            key = "#p0",
            unless = "#result == null || #result.isEmpty()"
    )
    public Optional<String> findCached(String configKey) {
        return load(configKey);
    }

    /**
     * pending 门禁存在或门禁状态未知时绕过缓存并直读主库。
     *
     * @param configKey 已通过白名单校验并规范化的平台配置键
     * @return 当前主库中的启用配置值
     */
    @DS(DataSourceName.MASTER)
    public Optional<String> findFresh(String configKey) {
        return load(configKey);
    }

    /**
     * 查询启用且未删除的平台配置，不缓存空值或数据库异常。
     *
     * @param configKey 已规范化的平台配置键
     * @return 去除首尾空白后的配置值
     */
    private Optional<String> load(String configKey) {
        SysConfigDO row = sysConfigMapper.selectOne(Wrappers.<SysConfigDO>lambdaQuery()
                .select(SysConfigDO::getConfigValue)
                .eq(SysConfigDO::getConfigKey, configKey)
                .eq(SysConfigDO::getStatus, ENABLED)
                .eq(SysConfigDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (row == null || !StringUtils.hasText(row.getConfigValue())) {
            return Optional.empty();
        }
        return Optional.of(row.getConfigValue().trim());
    }
}
