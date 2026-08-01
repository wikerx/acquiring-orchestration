package com.scott.payment.component.redis.cache.invalidation;

import com.scott.payment.component.core.cache.CacheMissMarkerStore;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.redis.cache.PaymentCacheRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ImmediateCacheEvictionService
 * @date : 2026-07-30 21:35
 * @email : scott_x@163.com
 * @description : 安全缓存立即精确失效服务，仅在 CacheManager 可用时注册；绕过事务感知缓存装饰器，并在商户资料场景协同删除正缓存和独立 miss marker
 * @status : create
 */
@Service
@ConditionalOnBean(CacheManager.class)
public class ImmediateCacheEvictionService {

    /**
     * 商户运行时资料 miss marker 所属业务域。
     */
    private static final String MERCHANT_DOMAIN = "merchant";

    /**
     * 商户运行时资料 miss marker 的业务用途。
     */
    private static final String RUNTIME_PROFILE_MISS_BUSINESS = "runtime-profile-miss";

    /**
     * Spring Cache 管理器，用于删除正缓存。
     */
    private final CacheManager cacheManager;

    /**
     * 独立 miss marker 存储；未启用 Redis 时允许为空，此时系统不会产生 marker。
     */
    private final CacheMissMarkerStore missMarkerStore;

    /**
     * 创建立即缓存失效服务。
     *
     * @param cacheManager            Spring Cache 管理器
     * @param missMarkerStoreProvider miss marker 存储提供器
     */
    public ImmediateCacheEvictionService(
            CacheManager cacheManager,
            ObjectProvider<CacheMissMarkerStore> missMarkerStoreProvider) {
        this.cacheManager = cacheManager;
        this.missMarkerStore = missMarkerStoreProvider.getIfAvailable();
    }

    /**
     * 立即删除已登记 Cache 的指定业务 Key。
     *
     * @param cacheName Spring Cache 名称
     * @param key       业务缓存 Key
     */
    public void evict(String cacheName, String key) {
        if (!PaymentCacheRegistry.defaultTtls().containsKey(cacheName)) {
            throw new IllegalArgumentException("Unregistered Redis cache name: " + cacheName);
        }
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Cache eviction key is required");
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new IllegalStateException("Registered Redis cache is unavailable: " + cacheName);
        }
        Cache target = cache instanceof TransactionAwareCacheDecorator decorator
                ? decorator.getTargetCache()
                : cache;
        target.evict(key.trim());
        if (PaymentCacheNames.MERCHANT_RUNTIME_PROFILE.equals(cacheName)
                && missMarkerStore != null) {
            /*
             * 正缓存和“不存在”marker 必须属于同一次可靠失效。marker 删除失败会向上抛出，
             * 使 Outbox 保留门禁并重试；重试期间读链会绕过两个缓存并查询主库。
             */
            missMarkerStore.evict(
                    MERCHANT_DOMAIN,
                    RUNTIME_PROFILE_MISS_BUSINESS,
                    key.trim()
            );
        }
    }
}
