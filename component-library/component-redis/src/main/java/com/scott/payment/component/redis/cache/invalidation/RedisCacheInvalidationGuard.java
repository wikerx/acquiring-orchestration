package com.scott.payment.component.redis.cache.invalidation;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheInvalidationGuard
 * @date : 2026-07-31 00:00
 * @email : scott_x@163.com
 * @description : Redis 基础设施层的受管永久缓存失效门禁，负责以 token 租约保护数据库变更至缓存精确删除完成之间的一致性窗口
 * @status : create
 *
 * <p>门禁只保护已登记的永久缓存。管理端在数据库事务写入前获取门禁，读取端在门禁存在期间
 * 绕过 Redis 并查询主库，Outbox 在事务提交后删除缓存并按 token 释放门禁。</p>
 */
@Service
public class RedisCacheInvalidationGuard implements CacheInvalidationGuard {

    /**
     * Redis 字符串操作入口，用于原子写入门禁和执行持有者校验 Lua。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 环境隔离 Redis Key 构造配置。
     */
    private final PaymentRedisProperties redisProperties;

    /**
     * Redis 业务指标记录器，不记录 cache businessKey 或门禁 token。
     */
    private final RedisBusinessMetrics metrics;

    /**
     * 创建安全缓存失效门禁服务。
     *
     * @param redisTemplate   Redis 字符串操作入口
     * @param redisProperties 环境隔离 Key 配置
     * @param metrics         Redis 业务指标记录器
     */
    @Autowired
    public RedisCacheInvalidationGuard(StringRedisTemplate redisTemplate,
                                       PaymentRedisProperties redisProperties,
                                       RedisBusinessMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.metrics = metrics;
    }

    /**
     * 创建不产生指标副作用的安全缓存失效门禁服务，供纯单元测试直接构造。
     *
     * @param redisTemplate   Redis 字符串操作入口
     * @param redisProperties 环境隔离 Key 配置
     */
    public RedisCacheInvalidationGuard(StringRedisTemplate redisTemplate,
                                       PaymentRedisProperties redisProperties) {
        this(redisTemplate, redisProperties, RedisBusinessMetrics.noop());
    }

    /**
     * 为指定受管缓存业务键获取唯一失效租约。
     * <p>
     * 租约通过 Redis {@code SET NX PX} 语义建立；已有发布者时拒绝并发执行。返回 token 仅用于
     * Lua 校验持有者，不写入日志。
     * </p>
     *
     * @param cacheName   受支持的永久缓存名称
     * @param businessKey 商户号或平台配置键
     * @param ttl        门禁最长持有时间
     * @return 包含持有者 token 的失效租约
     */
    @Override
    public CacheInvalidationLease acquire(String cacheName, String businessKey, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache invalidation gate TTL must be positive");
        }
        String normalizedBusinessKey = requireBusinessKey(businessKey);
        String token = "t-" + UUID.randomUUID();
        long startNanos = System.nanoTime();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    pendingKey(cacheName, normalizedBusinessKey),
                    token,
                    ttl
            );
            if (!Boolean.TRUE.equals(acquired)) {
                record(
                        RedisBusinessMetrics.Operation.ACQUIRE,
                        RedisBusinessMetrics.Outcome.CONTENDED,
                        startNanos
                );
                throw new IllegalStateException("Managed cache invalidation is already in progress");
            }
            record(
                    RedisBusinessMetrics.Operation.ACQUIRE,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    startNanos
            );
            return new CacheInvalidationLease(cacheName, normalizedBusinessKey, token);
        } catch (RuntimeException exception) {
            if (!StringUtils.hasText(exception.getMessage())
                    || !exception.getMessage().contains("already in progress")) {
                record(
                        RedisBusinessMetrics.Operation.ACQUIRE,
                        RedisBusinessMetrics.Outcome.ERROR,
                        startNanos
                );
            }
            throw exception;
        }
    }

    /**
     * 判断指定受管缓存业务键是否仍处于失效保护窗口。
     *
     * @param cacheName   受支持的永久缓存名称
     * @param businessKey 商户号或平台配置键
     * @return Redis 明确存在门禁时返回 {@code true}
     * @throws IllegalStateException Redis 未返回确定状态时抛出，避免错误放行旧安全配置
     */
    @Override
    public boolean isPending(String cacheName, String businessKey) {
        long startNanos = System.nanoTime();
        try {
            Boolean pending = redisTemplate.hasKey(pendingKey(cacheName, requireBusinessKey(businessKey)));
            if (pending == null) {
                throw new IllegalStateException("Redis returned an unknown cache invalidation state");
            }
            record(
                    RedisBusinessMetrics.Operation.READ,
                    pending
                            ? RedisBusinessMetrics.Outcome.PENDING
                            : RedisBusinessMetrics.Outcome.SUCCESS,
                    startNanos
            );
            return pending;
        } catch (RuntimeException exception) {
            record(
                    RedisBusinessMetrics.Operation.READ,
                    RedisBusinessMetrics.Outcome.ERROR,
                    startNanos
            );
            throw exception;
        }
    }

    /**
     * 使用 Lua 仅由当前 token 持有者释放失效门禁。
     *
     * @param lease 获取门禁时返回的租约
     * @return 当前持有者成功释放时返回 {@code true}
     */
    @Override
    public boolean release(CacheInvalidationLease lease) {
        if (lease == null || !StringUtils.hasText(lease.token())) {
            return false;
        }
        long startNanos = System.nanoTime();
        try {
            Long released = redisTemplate.execute(
                    PaymentRedisScripts.lockReleaseV1(),
                    List.of(pendingKey(lease.cacheName(), requireBusinessKey(lease.businessKey()))),
                    lease.token()
            );
            boolean success = Long.valueOf(1L).equals(released);
            record(
                    RedisBusinessMetrics.Operation.RELEASE,
                    success
                            ? RedisBusinessMetrics.Outcome.SUCCESS
                            : RedisBusinessMetrics.Outcome.CONTENDED,
                    startNanos
            );
            return success;
        } catch (RuntimeException exception) {
            record(
                    RedisBusinessMetrics.Operation.RELEASE,
                    RedisBusinessMetrics.Outcome.ERROR,
                    startNanos
            );
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.LOCK_RELEASE,
                    metrics.classifyFailure(exception)
            );
            throw exception;
        }
    }

    /**
     * 记录受管缓存失效门禁操作，不暴露业务键和持有者 token。
     *
     * @param operation  门禁操作
     * @param outcome    操作结果
     * @param startNanos 本次 Redis 操作起始时间
     */
    private void record(RedisBusinessMetrics.Operation operation,
                        RedisBusinessMetrics.Outcome outcome,
                        long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.CACHE_INVALIDATION,
                operation,
                outcome,
                System.nanoTime() - startNanos
        );
    }

    /**
     * 构造受管永久缓存的失效门禁 Key。
     * <p>
     * 门禁紧邻对应永久缓存命名空间，便于按业务域识别，同时不会与 Spring Cache 的实际
     * 业务 Key 冲突。物理格式分别为：
     * {@code acquiring:{environment}:merchant:info:pending:{merchantId}}、
     * {@code acquiring:{environment}:merchant:openapi:pending:{merchantId}}、
     * {@code acquiring:{environment}:merchant:keyMeta:pending:{merchantId}}、
     * {@code acquiring:{environment}:merchant:route:pending:{merchantId}} 和
     * {@code acquiring:{environment}:config:public:pending:{configKey}}。
     * </p>
     *
     * @param cacheName   Spring Cache 名称
     * @param businessKey 已校验的业务键
     * @return 环境隔离的 Redis 物理 Key
     */
    private String pendingKey(String cacheName, String businessKey) {
        return switch (cacheName) {
            case PaymentCacheNames.MERCHANT_RUNTIME_PROFILE ->
                    redisProperties.businessKey("merchant", "info", "pending", businessKey);
            case PaymentCacheNames.MERCHANT_OPENAPI_ACCESS ->
                    redisProperties.businessKey("merchant", "openapi", "pending", businessKey);
            case PaymentCacheNames.MERCHANT_KEY_METADATA ->
                    redisProperties.businessKey("merchant", "keyMeta", "pending", businessKey);
            case PaymentCacheNames.MERCHANT_ROUTE ->
                    redisProperties.businessKey("merchant", "route", "pending", businessKey);
            case PaymentCacheNames.PLATFORM_CONFIG ->
                    redisProperties.businessKey("config", "public", "pending", businessKey);
            default -> throw new IllegalArgumentException(
                    "Cache invalidation guard does not allow cache name: " + cacheName
            );
        };
    }

    /**
     * 校验并去除缓存业务键首尾空白。
     *
     * @param businessKey 原始业务键
     * @return 非空规范业务键
     * @throws IllegalArgumentException 业务键为空时抛出
     */
    private String requireBusinessKey(String businessKey) {
        if (!StringUtils.hasText(businessKey)) {
            throw new IllegalArgumentException("Cache invalidation business key is required");
        }
        return businessKey.trim();
    }
}
