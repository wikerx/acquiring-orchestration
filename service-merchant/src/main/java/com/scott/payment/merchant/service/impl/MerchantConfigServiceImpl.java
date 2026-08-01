package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.cache.PlatformConfigCachePolicy;
import com.scott.payment.merchant.service.MerchantConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
     * 记录门禁查询降级，不打印平台配置值。
     */
    private static final Logger log = LoggerFactory.getLogger(MerchantConfigServiceImpl.class);

    /**
     * 永久缓存失效门禁；pending 或状态未知时禁止使用可能过期的缓存值。
     */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 平台公开配置读取器，分别提供缓存代理入口和主库直读入口。
     */
    private final PlatformConfigCacheReader cacheReader;

    /**
     * 创建商户端只读参数服务。
     *
     * @param invalidationGuard 永久缓存失效门禁
     * @param cacheReader       平台公开配置读取器
     */
    public MerchantConfigServiceImpl(CacheInvalidationGuard invalidationGuard,
                                     PlatformConfigCacheReader cacheReader) {
        this.invalidationGuard = invalidationGuard;
        this.cacheReader = cacheReader;
    }

    /**
     * 查询启用且未删除的平台公开参数值。
     *
     * <p>方法在缓存读取前后各检查一次 pending 门禁：前置检查覆盖已经开始的管理端变更，
     * 后置检查覆盖缓存读取过程中刚开始的变更。任一检查命中或 Redis 状态不可判定时均丢弃
     * 缓存结果并查询主库。</p>
     *
     * @param configKey 参数键名
     * @return 参数值；不存在、停用或空值时返回空
     */
    @Override
    public Optional<String> enabledConfigValue(String configKey) {
        if (!PlatformConfigCachePolicy.isCacheable(configKey)) {
            return Optional.empty();
        }
        String normalizedConfigKey = configKey.trim();
        if (mustBypassCache(normalizedConfigKey)) {
            return cacheReader.findFresh(normalizedConfigKey);
        }
        Optional<String> cached = cacheReader.findCached(normalizedConfigKey);
        return mustBypassCache(normalizedConfigKey)
                ? cacheReader.findFresh(normalizedConfigKey)
                : cached;
    }

    /**
     * 判断平台配置读取是否必须绕过缓存。
     *
     * <p>公开配置不是资金事实数据，Redis 故障时允许降级查询主库；但不能继续使用无法确认
     * 是否过期的永久缓存值。</p>
     *
     * @param configKey 已规范化的平台配置键
     * @return true 表示必须绕过缓存并查询主库
     */
    private boolean mustBypassCache(String configKey) {
        try {
            return invalidationGuard.isPending(PaymentCacheNames.PLATFORM_CONFIG, configKey);
        } catch (RuntimeException exception) {
            log.warn(
                    "event: PLATFORM_CONFIG_CACHE_GUARD_CHECK_FAILED "
                            + "exceptionType: {} reason: {}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return true;
        }
    }
}
