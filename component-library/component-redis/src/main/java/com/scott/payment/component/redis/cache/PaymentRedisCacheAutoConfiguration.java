package com.scott.payment.component.redis.cache;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.cache.CacheMissMarkerStore;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.redis.cache.invalidation.ImmediateCacheEvictionService;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisCacheAutoConfiguration
 * @date : 2026-07-30 22:05
 * @email : scott_x@163.com
 * @description : 支付系统 Redis Cache 自动配置，集中注册业务读模型生命周期、统一序列化、故障降级和独立 miss marker 存储
 * @status : update
 */
@Slf4j
@AutoConfiguration(after = RedisAutoConfiguration.class)
@EnableCaching
@EnableConfigurationProperties({PaymentCacheProperties.class, PaymentRedisProperties.class})
@ConditionalOnProperty(prefix = "payment.cache.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRedisCacheAutoConfiguration {

    /**
     * 注册直连 Redis 的 miss marker 存储，使业务缓存能够区分“数据库不存在”和“Redis 读取失败”。
     *
     * @param stringRedisTemplate Redis 字符串访问模板
     * @param keyResolver         统一 Redis 业务 Key 解析器
     * @param metricsProvider     Redis 业务指标记录器提供器
     * @return miss marker 存储实现
     */
    @Bean
    @ConditionalOnMissingBean(CacheMissMarkerStore.class)
    public CacheMissMarkerStore cacheMissMarkerStore(StringRedisTemplate stringRedisTemplate,
                                                     PaymentRedisKeyResolver keyResolver,
                                                     ObjectProvider<RedisBusinessMetrics> metricsProvider) {
        return new RedisCacheMissMarkerStore(
                stringRedisTemplate,
                keyResolver,
                metricsProvider.getIfAvailable(RedisBusinessMetrics::noop)
        );
    }

    /**
     * 注册 Redis CacheManager。
     * <p>
     * 缓存只用于高频读路径减压；交易状态、金额、回调结果等事实数据仍必须以数据库为准。
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @param properties             缓存配置
     * @return Redis CacheManager
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory,
                                          PaymentCacheProperties properties) {
        RedisCacheConfiguration defaultConfiguration = cacheConfiguration(
                ttlOrDefault(properties.getDefaultTtl(), Duration.ofMinutes(10)),
                properties.getKeyPrefix(),
                properties.getTtlJitterPercent());
        Map<String, Duration> resolvedTtls = PaymentCacheRegistry.resolveTtls(properties.getTtl());
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        resolvedTtls.forEach((cacheName, ttl) -> cacheConfigurations.put(
                cacheName,
                cacheConfiguration(
                        ttl,
                        properties.getKeyPrefix(),
                        properties.getTtlJitterPercent())
        ));
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
                redisConnectionFactory,
                BatchStrategies.scan(1000)
        );
        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaultConfiguration)
                .withInitialCacheConfigurations(cacheConfigurations)
                .enableStatistics()
                .transactionAware()
                .build();
    }

    /**
     * 注册安全读模型的立即失效服务。
     * <p>
     * 该服务与 CacheManager 属于同一自动配置边界，必须显式注册，避免业务应用扫描组件时
     * CacheManager 尚未完成装配，导致管理端 Outbox 缺失精确删除能力。
     *
     * @param cacheManager            Spring Cache 管理器
     * @param missMarkerStoreProvider 商户不存在 marker 存储提供器
     * @return 可立即删除正缓存和 miss marker 的失效服务
     */
    @Bean
    @ConditionalOnMissingBean(ImmediateCacheEvictionService.class)
    public ImmediateCacheEvictionService immediateCacheEvictionService(
            CacheManager cacheManager,
            ObjectProvider<CacheMissMarkerStore> missMarkerStoreProvider) {
        return new ImmediateCacheEvictionService(cacheManager, missMarkerStoreProvider);
    }

    /**
     * 注册跨服务共享的缓存 generation 存储，供管理端写链路和业务读模型使用同一原子协议。
     *
     * @param stringRedisTemplate Redis 字符串模板
     * @param redisProperties Redis Key 规范配置
     * @param metricsProvider Redis 指标提供器
     * @return generation 门禁与切换服务
     */
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(RedisCacheGenerationStore.class)
    public RedisCacheGenerationStore redisCacheGenerationStore(
            StringRedisTemplate stringRedisTemplate,
            PaymentRedisProperties redisProperties,
            ObjectProvider<RedisBusinessMetrics> metricsProvider) {
        return new RedisCacheGenerationStore(
                stringRedisTemplate,
                redisProperties,
                metricsProvider.getIfAvailable(RedisBusinessMetrics::noop));
    }

    /**
     * 缓存属于数据库读路径的减压层，Redis 短暂异常时回源数据库，不能直接中断交易查询或商户校验。
     *
     * <p>该降级只影响 Spring Cache。请求幂等、MQ 去重、全局编号和风控并发计数仍使用各自的
     * 强一致失败策略，不会被此处理器静默放行。</p>
     *
     * @return Spring Cache 异常处理器
     */
    @Bean
    @ConditionalOnMissingBean(CacheErrorHandler.class)
    public CacheErrorHandler paymentCacheErrorHandler(
            ObjectProvider<RedisBusinessMetrics> metricsProvider) {
        return paymentCacheErrorHandler(metricsProvider.getIfAvailable(RedisBusinessMetrics::noop));
    }

    /**
     * 构建可注入指标记录器的缓存异常处理器，供自动配置和纯单元测试复用。
     *
     * @param metrics Redis 业务指标记录器
     * @return Spring Cache 异常处理器
     */
    CacheErrorHandler paymentCacheErrorHandler(RedisBusinessMetrics metrics) {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                metrics.recordOperation(
                        RedisBusinessMetrics.Feature.CACHE,
                        RedisBusinessMetrics.Operation.READ,
                        RedisBusinessMetrics.Outcome.FALLBACK,
                        0L
                );
                metrics.recordFallback(
                        RedisBusinessMetrics.Feature.CACHE,
                        RedisBusinessMetrics.FallbackReason.OPERATION_FAILURE
                );
                logCacheFailure("GET", cache, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                metrics.recordOperation(
                        RedisBusinessMetrics.Feature.CACHE,
                        RedisBusinessMetrics.Operation.WRITE,
                        RedisBusinessMetrics.Outcome.ERROR,
                        0L
                );
                logCacheFailure("PUT", cache, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                metrics.recordOperation(
                        RedisBusinessMetrics.Feature.CACHE,
                        RedisBusinessMetrics.Operation.EVICT,
                        RedisBusinessMetrics.Outcome.ERROR,
                        0L
                );
                logCacheFailure("EVICT", cache, exception);
                rethrowForSecurityCache(cache, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                metrics.recordOperation(
                        RedisBusinessMetrics.Feature.CACHE,
                        RedisBusinessMetrics.Operation.CLEAR,
                        RedisBusinessMetrics.Outcome.ERROR,
                        0L
                );
                logCacheFailure("CLEAR", cache, exception);
                rethrowForSecurityCache(cache, exception);
            }
        };
    }

    RedisCacheConfiguration cacheConfiguration(Duration ttl, String keyPrefix) {
        return cacheConfiguration(ttl, keyPrefix, 0);
    }

    RedisCacheConfiguration cacheConfiguration(Duration ttl, String keyPrefix, int ttlJitterPercent) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        PaymentRedisSerializerFactory.create()));
        if (ttl == null || ttl.isNegative()) {
            throw new IllegalArgumentException("Redis cache TTL must not be null or negative");
        }
        // Duration.ZERO 表示常驻快照；不挂载抖动函数，确保 Redis 写入不携带过期时间。
        if (!ttl.isZero()) {
            configuration = configuration.entryTtl(new PaymentRedisCacheTtlFunction(ttl, ttlJitterPercent));
        }
        if (StringUtils.hasText(keyPrefix)) {
            String normalizedPrefix = normalizeKeyPrefix(keyPrefix);
            configuration = configuration.computePrefixWith(
                    cacheName -> normalizedPrefix + cacheName + ":");
        }
        return configuration;
    }

    private String normalizeKeyPrefix(String keyPrefix) {
        String normalized = keyPrefix.trim();
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + ":";
    }

    /**
     * 选择有效 TTL，防止空值、零值或负值产生无界缓存。
     *
     * @param ttl        当前 Cache Name 配置的 TTL
     * @param defaultTtl 全局默认 TTL
     * @return 首个有效 TTL；两者均无效时使用 10 分钟安全默认值
     */
    private Duration ttlOrDefault(Duration ttl, Duration defaultTtl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return defaultTtl == null || defaultTtl.isZero() || defaultTtl.isNegative()
                    ? Duration.ofMinutes(10)
                    : defaultTtl;
        }
        return ttl;
    }

    /**
     * 记录 Redis Cache 操作失败的结构化事件，不输出缓存 Key 或 Value。
     *
     * @param operation 缓存操作类型
     * @param cache     发生失败的缓存；无法解析时可为空
     * @param exception Redis Cache 运行时异常
     */
    private void logCacheFailure(String operation, Cache cache, RuntimeException exception) {
        log.warn("event: REDIS_CACHE_OPERATION_FAILED operation: {} cacheName: {} exceptionType: {}",
                operation,
                cache == null ? "UNKNOWN" : cache.getName(),
                exception.getClass().getSimpleName());
    }

    /**
     * 对安全配置缓存的失效失败执行 fail-closed。
     * <p>
     * 普通查询缓存可由错误处理器记录后回源，但商户运行配置和 OpenAPI 访问权限若删除失败，
     * 继续提交更新可能暴露旧权限，因此必须把异常返回调用方。
     * </p>
     *
     * @param cache     当前缓存
     * @param exception 原始失效异常
     */
    private void rethrowForSecurityCache(Cache cache, RuntimeException exception) {
        String cacheName = cache == null ? null : cache.getName();
        if (PaymentCacheNames.MERCHANT_RUNTIME_PROFILE.equals(cacheName)
                || PaymentCacheNames.MERCHANT_OPENAPI_ACCESS.equals(cacheName)
                || PaymentCacheNames.MERCHANT_KEY_METADATA.equals(cacheName)
                || PaymentCacheNames.MERCHANT_ROUTE.equals(cacheName)
                || PaymentCacheNames.MERCHANT_ACTIVE_FEE.equals(cacheName)
                || PaymentCacheNames.ISO_COUNTRY.equals(cacheName)
                || PaymentCacheNames.ISO_CURRENCY.equals(cacheName)
                || PaymentCacheNames.SYSTEM_CONFIG.equals(cacheName)) {
            throw exception;
        }
    }
}
