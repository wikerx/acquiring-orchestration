package com.scott.payment.component.redis.cache;

import com.scott.payment.component.core.cache.PaymentCacheNames;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCacheRegistry
 * @date : 2026-07-30 09:35
 * @email : scott_x@163.com
 * @description : 支付系统特殊 Spring Cache 生命周期注册表；普通缓存使用 CacheManager 默认 TTL 动态创建
 * @status : create
 */
public final class PaymentCacheRegistry {

    /**
     * 已登记缓存的生命周期策略。
     *
     * <p>{@link Duration#ZERO} 明确表示常驻缓存。常驻只描述 Redis 不设置物理过期时间，
     * 数据仍由数据库提供事实来源，并由管理端变更链路可靠失效后按需重建。</p>
     */
    private static final Map<String, Duration> DEFAULT_TTLS = Map.of(
            PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, Duration.ZERO,
            PaymentCacheNames.MERCHANT_OPENAPI_ACCESS, Duration.ZERO,
            PaymentCacheNames.MERCHANT_KEY_METADATA, Duration.ZERO,
            PaymentCacheNames.MERCHANT_ROUTE, Duration.ZERO,
            PaymentCacheNames.SYSTEM_CONFIG, Duration.ZERO,
            PaymentCacheNames.ADMIN_USER_PROFILE, Duration.ZERO,
            PaymentCacheNames.CARD_BIN, Duration.ZERO
    );

    private PaymentCacheRegistry() {
    }

    /**
     * 获取已登记 Cache 的生命周期策略。
     *
     * @return 不可变的 Cache Name 与生命周期映射；零值表示不设置物理 TTL
     */
    public static Map<String, Duration> defaultTtls() {
        return DEFAULT_TTLS;
    }

    /**
     * 校验并合并 Cache 生命周期配置。
     *
     * <p>当前登记项均属于业务常驻快照，部署配置只能显式声明为零，不能临时改成
     * 有限 TTL 掩盖失效链路问题。普通有限期缓存无需登记，直接使用 CacheManager 默认 TTL；
     * 只有永久缓存或需要特殊 TTL 的缓存才进入本注册表。</p>
     *
     * @param overrides 配置中心提供的生命周期覆盖；零值表示常驻
     * @return 包含全部已登记 Cache 的有效生命周期
     * @throws IllegalArgumentException Cache Name 未登记、生命周期为空/为负数，或常驻策略被改写时抛出
     */
    public static Map<String, Duration> resolveTtls(Map<String, Duration> overrides) {
        Map<String, Duration> resolved = new LinkedHashMap<>(DEFAULT_TTLS);
        if (overrides == null) {
            return resolved;
        }
        overrides.forEach((cacheName, ttl) -> {
            if (!DEFAULT_TTLS.containsKey(cacheName)) {
                throw new IllegalArgumentException("Unregistered Redis cache name: " + cacheName);
            }
            if (ttl == null || ttl.isNegative()) {
                throw new IllegalArgumentException("Redis cache TTL must not be null or negative: " + cacheName);
            }
            Duration registeredTtl = DEFAULT_TTLS.get(cacheName);
            if (registeredTtl.isZero() && !ttl.isZero()) {
                throw new IllegalArgumentException("Persistent Redis cache TTL must remain zero: " + cacheName);
            }
            resolved.put(cacheName, ttl);
        });
        return resolved;
    }
}
