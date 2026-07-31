package com.scott.payment.component.redis.cache;

import com.scott.payment.component.core.cache.CacheMissMarkerStore;
import com.scott.payment.component.core.cache.PaymentCacheTtlPolicy;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheMissMarkerStore
 * @date : 2026-07-30 21:20
 * @email : scott_x@163.com
 * @description : 基于 Redis String 的空结果标记存储实现，使用统一环境 Key 前缀和有界 TTL 抖动，并保留读取故障与业务未命中的语义差异
 * @status : create
 */
@Slf4j
public class RedisCacheMissMarkerStore implements CacheMissMarkerStore {

    /**
     * marker 固定值；Value 不承载业务数据，只表达数据库曾确认记录不存在。
     */
    private static final String MISS_MARKER_VALUE = "1";

    /**
     * Redis 字符串访问模板；由 Spring Boot Redis 自动配置提供，不允许为空。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 统一 Redis Key 解析器；负责生成 acquiring:{environment}:{domain}:{business}:{businessKey}。
     */
    private final PaymentRedisKeyResolver keyResolver;

    /**
     * Redis 业务指标记录器，不记录 marker 业务键。
     */
    private final RedisBusinessMetrics metrics;

    /**
     * 创建 Redis miss marker 存储。
     *
     * @param stringRedisTemplate Redis 字符串访问模板
     * @param keyResolver         统一 Redis Key 解析器
     * @param metrics             Redis 业务指标记录器
     */
    public RedisCacheMissMarkerStore(StringRedisTemplate stringRedisTemplate,
                                     PaymentRedisKeyResolver keyResolver,
                                     RedisBusinessMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyResolver = keyResolver;
        this.metrics = metrics;
    }

    /**
     * 创建不产生指标副作用的 Redis miss marker 存储，供纯单元测试直接构造。
     *
     * @param stringRedisTemplate Redis 字符串访问模板
     * @param keyResolver         统一 Redis Key 解析器
     */
    public RedisCacheMissMarkerStore(StringRedisTemplate stringRedisTemplate,
                                     PaymentRedisKeyResolver keyResolver) {
        this(stringRedisTemplate, keyResolver, RedisBusinessMetrics.noop());
    }

    /**
     * 查询 miss marker；Redis 故障返回 UNAVAILABLE，使调用方继续走数据库安全路径。
     *
     * @param domain      业务域
     * @param business    miss marker 业务用途
     * @param businessKey 业务唯一键
     * @return marker 查询三态
     */
    @Override
    public LookupStatus lookup(String domain, String business, String businessKey) {
        long startNanos = System.nanoTime();
        try {
            String marker = stringRedisTemplate.opsForValue().get(
                    keyResolver.businessKey(domain, business, businessKey)
            );
            LookupStatus status = marker == null ? LookupStatus.ABSENT : LookupStatus.PRESENT;
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.CACHE_MISS_MARKER,
                    RedisBusinessMetrics.Operation.READ,
                    marker == null
                            ? RedisBusinessMetrics.Outcome.MISS
                            : RedisBusinessMetrics.Outcome.HIT,
                    System.nanoTime() - startNanos
            );
            return status;
        } catch (RuntimeException exception) {
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.CACHE_MISS_MARKER,
                    RedisBusinessMetrics.Operation.READ,
                    RedisBusinessMetrics.Outcome.FALLBACK,
                    System.nanoTime() - startNanos
            );
            metrics.recordFallback(
                    RedisBusinessMetrics.Feature.CACHE_MISS_MARKER,
                    metrics.classifyFallback(exception)
            );
            log.warn(
                    "event: REDIS_CACHE_MISS_MARKER_READ_FAILED domain: {} business: {} "
                            + "exceptionType: {} reason: {}",
                    domain,
                    business,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return LookupStatus.UNAVAILABLE;
        }
    }

    /**
     * 写入经统一策略抖动后的短 TTL marker。
     *
     * @param domain        业务域
     * @param business      miss marker 业务用途
     * @param businessKey   业务唯一键
     * @param baseTtl       基础有效期
     * @param jitterPercent TTL 抖动百分比
     */
    @Override
    public void markMissing(String domain,
                            String business,
                            String businessKey,
                            Duration baseTtl,
                            int jitterPercent) {
        Duration effectiveTtl = PaymentCacheTtlPolicy.jitter(baseTtl, jitterPercent);
        long startNanos = System.nanoTime();
        try {
            stringRedisTemplate.opsForValue().set(
                    keyResolver.businessKey(domain, business, businessKey),
                    MISS_MARKER_VALUE,
                    effectiveTtl
            );
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.CACHE_MISS_MARKER,
                    RedisBusinessMetrics.Operation.WRITE,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    System.nanoTime() - startNanos
            );
        } catch (RuntimeException exception) {
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.CACHE_MISS_MARKER,
                    RedisBusinessMetrics.Operation.WRITE,
                    RedisBusinessMetrics.Outcome.ERROR,
                    System.nanoTime() - startNanos
            );
            throw exception;
        }
    }

    /**
     * 删除 miss marker；删除失败直接抛出，由可靠失效链保留门禁并重试。
     *
     * @param domain      业务域
     * @param business    miss marker 业务用途
     * @param businessKey 业务唯一键
     */
    @Override
    public void evict(String domain, String business, String businessKey) {
        long startNanos = System.nanoTime();
        try {
            stringRedisTemplate.delete(keyResolver.businessKey(domain, business, businessKey));
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.CACHE_MISS_MARKER,
                    RedisBusinessMetrics.Operation.EVICT,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    System.nanoTime() - startNanos
            );
        } catch (RuntimeException exception) {
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.CACHE_MISS_MARKER,
                    RedisBusinessMetrics.Operation.EVICT,
                    RedisBusinessMetrics.Outcome.ERROR,
                    System.nanoTime() - startNanos
            );
            throw exception;
        }
    }
}
