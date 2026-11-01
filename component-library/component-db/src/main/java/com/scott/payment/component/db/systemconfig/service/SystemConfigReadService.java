package com.scott.payment.component.db.systemconfig.service;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.systemconfig.model.SystemConfigSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 跨服务系统参数统一读取门面，协调永久缓存、失效门禁和主库降级
 */
@Slf4j
@Service
public class SystemConfigReadService {

    /**
     * 变更进行中时阻止读取永久旧缓存。
     */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 提供受 Spring 代理的缓存读取和强制主库回源入口。
     */
    private final SystemConfigCacheReader cacheReader;

    /**
     * 创建跨服务系统参数读取服务。
     *
     * @param invalidationGuard 永久缓存失效门禁
     * @param cacheReader 缓存代理与主库读取器
     */
    public SystemConfigReadService(CacheInvalidationGuard invalidationGuard,
                                   SystemConfigCacheReader cacheReader) {
        this.invalidationGuard = invalidationGuard;
        this.cacheReader = cacheReader;
    }

    /**
     * 按全局唯一参数键查询未删除配置快照。
     *
     * <p>缓存读取前后都检查 pending 门禁。任一次检查命中或 Redis 状态不可判定时，
     * 丢弃缓存结果并读取主库，避免管理端修改期间返回永久旧值。</p>
     *
     * @param configKey 参数键名
     * @return 未删除配置快照；参数为空或记录不存在时返回空
     */
    public Optional<SystemConfigSnapshot> findByKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return Optional.empty();
        }
        String normalizedConfigKey = configKey.trim();
        if (mustBypassCache(normalizedConfigKey)) {
            return Optional.ofNullable(cacheReader.findFresh(normalizedConfigKey));
        }
        SystemConfigSnapshot cached = cacheReader.findCached(normalizedConfigKey);
        return mustBypassCache(normalizedConfigKey)
                ? Optional.ofNullable(cacheReader.findFresh(normalizedConfigKey))
                : Optional.ofNullable(cached);
    }

    /**
     * 按全局唯一参数键查询可供运行服务使用的配置值。
     *
     * @param configKey 参数键名
     * @return 配置启用且值非空时返回值，否则返回空
     */
    public Optional<String> findEnabledValue(String configKey) {
        return findByKey(configKey).flatMap(SystemConfigSnapshot::enabledValue);
    }

    /**
     * 检查配置失效事件是否仍在传播；门禁不可用时保守回源主库。
     *
     * @param configKey 全局唯一配置键
     * @return 必须绕过缓存时返回 true
     */
    private boolean mustBypassCache(String configKey) {
        try {
            return invalidationGuard.isPending(PaymentCacheNames.SYSTEM_CONFIG, configKey);
        } catch (RuntimeException exception) {
            log.warn(
                    "系统配置缓存门禁检查失败, event: SYSTEM_CONFIG_CACHE_GUARD_CHECK_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName()
            );
            return true;
        }
    }
}
