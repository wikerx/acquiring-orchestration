package com.scott.payment.component.redis.cache;

import com.scott.payment.component.core.cache.PaymentCacheTtlPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCacheProperties
 * @date : 2026-07-30 09:35
 * @email : scott_x@163.com
 * @description : 支付系统 Redis 缓存配置，区分常驻业务快照与有限期恢复性数据
 * @status : update
 */
@ConfigurationProperties(prefix = "payment.cache.redis")
public class PaymentCacheProperties {

    /**
     * 是否启用基于 Spring Cache 的 Redis 缓存。
     */
    private boolean enabled = true;

    /**
     * 缓存 Key 前缀，用于区分同一 Redis 集群内的系统和部署环境；不能为空。
     */
    private String keyPrefix = "acquiring:local";

    /**
     * 默认缓存有效期。
     */
    private Duration defaultTtl = Duration.ofMinutes(10);

    /**
     * 有限期缓存 TTL 随机抖动百分比，取值 0~50；0 表示关闭抖动。
     * 常驻缓存不应用此配置。
     */
    private int ttlJitterPercent = 10;

    /**
     * 安全缓存可靠失效门禁 TTL。
     *
     * <p>门禁是数据库变更至缓存删除完成之间的恢复性租约，不是业务缓存生命周期；
     * 到期仅用于清理异常遗留标记，不能替代 Outbox 重试。</p>
     */
    private Duration invalidationGateTtl = Duration.ofHours(2);

    /**
     * 按缓存名称配置的生命周期；零值表示常驻且不设置物理 TTL。
     */
    private Map<String, Duration> ttl = new HashMap<>();

    /**
     * 创建缓存配置并加载 Registry 中的 Cache Name 与生命周期约束。
     *
     * <p>配置中心可以显式重复声明已登记策略，但不能改变常驻缓存的生命周期语义。</p>
     */
    public PaymentCacheProperties() {
        ttl.putAll(PaymentCacheRegistry.defaultTtls());
    }

    /**
     * 判断 Spring Cache Redis 实现是否启用。
     *
     * @return true 表示注册 RedisCacheManager，false 表示不启用该自动配置
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 配置 Spring Cache Redis 实现开关。
     *
     * @param enabled 是否启用 RedisCacheManager
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 Spring Cache 物理 Key 前缀。
     *
     * @return 包含系统与环境隔离维度的前缀，末尾分隔符由自动配置统一规范化
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 配置 Spring Cache 物理 Key 前缀。
     *
     * @param keyPrefix 系统和环境前缀，不得混用其他部署环境
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 获取未单独登记 Cache 使用的默认 TTL。
     *
     * @return 正数 Duration，默认 10 分钟
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    /**
     * 配置未单独登记 Cache 使用的默认 TTL。
     *
     * @param defaultTtl 默认缓存生命周期；最终由自动配置校验并回退到安全默认值
     */
    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * 获取缓存 TTL 随机抖动百分比。
     *
     * @return 0~50 的抖动百分比
     */
    public int getTtlJitterPercent() {
        return ttlJitterPercent;
    }

    /**
     * 配置缓存 TTL 随机抖动百分比。
     *
     * @param ttlJitterPercent 0~50 的抖动百分比
     */
    public void setTtlJitterPercent(int ttlJitterPercent) {
        if (ttlJitterPercent < 0 || ttlJitterPercent > PaymentCacheTtlPolicy.MAX_JITTER_PERCENT) {
            throw new IllegalArgumentException("Redis cache TTL jitter percent must be between 0 and "
                    + PaymentCacheTtlPolicy.MAX_JITTER_PERCENT);
        }
        this.ttlJitterPercent = ttlJitterPercent;
    }

    /**
     * 获取已登记 Cache Name 的生命周期配置。
     *
     * @return Cache Name 到 Duration 的映射；零值表示不设置物理 TTL
     */
    public Map<String, Duration> getTtl() {
        return ttl;
    }

    /**
     * 配置已登记 Cache Name 的生命周期；未知 Cache Name 或常驻策略改写会在创建
     * CacheManager 时被拒绝。
     *
     * @param ttl Cache Name 到 TTL 的映射；null 按空映射处理
     */
    public void setTtl(Map<String, Duration> ttl) {
        this.ttl = ttl == null ? new HashMap<>() : ttl;
    }

    /**
     * 获取安全缓存失效门禁 TTL。
     *
     * @return 用于异常恢复的正数 Duration
     */
    public Duration getInvalidationGateTtl() {
        return invalidationGateTtl;
    }

    /**
     * 配置安全缓存失效门禁 TTL。
     *
     * @param invalidationGateTtl 正数 Duration；非正值立即拒绝
     */
    public void setInvalidationGateTtl(Duration invalidationGateTtl) {
        if (invalidationGateTtl == null
                || invalidationGateTtl.isZero()
                || invalidationGateTtl.isNegative()) {
            throw new IllegalArgumentException("Cache invalidation gate TTL must be positive");
        }
        this.invalidationGateTtl = invalidationGateTtl;
    }
}
