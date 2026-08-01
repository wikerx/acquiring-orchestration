package com.scott.payment.component.redis.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scott.payment.component.core.cache.CacheMissMarkerStore;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.redis.cache.invalidation.ImmediateCacheEvictionService;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 支付系统 Redis Cache 物理 Key 与过期策略测试。
 */
@Slf4j
class PaymentRedisCacheAutoConfigurationTests {

    private final PaymentRedisCacheAutoConfiguration autoConfiguration =
            new PaymentRedisCacheAutoConfiguration();

    /**
     * Redis Cache 开启时必须同时提供立即失效服务，使管理端 Outbox 能够删除安全读模型。
     * 该 Bean 必须由 Redis Cache 自动配置注册，不能依赖业务应用的组件扫描顺序。
     */
    @Test
    void shouldRegisterImmediateEvictionServiceWhenRedisCacheIsEnabled() {
        log.info("测试 Redis Cache 自动装配，关键输入: 容器已提供 CacheManager 和 miss marker 存储");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PaymentRedisCacheAutoConfiguration.class))
                .withBean(CacheManager.class, () -> mock(CacheManager.class))
                .withBean(CacheMissMarkerStore.class, () -> mock(CacheMissMarkerStore.class))
                .run(context -> assertThat(context)
                        .hasSingleBean(ImmediateCacheEvictionService.class));

        log.info("Redis Cache 自动装配验证完成，结果: 立即失效服务已注册");
    }

    /**
     * Nacos YAML 中包含冒号的 Cache Name 必须使用方括号 Map Key，
     * 防止 Spring Boot 绑定时把领域分隔符删除并生成未登记名称。
     */
    @Test
    void shouldPreserveRegisteredCacheNamesInRedisDevYaml() throws IOException {
        Path configPath = redisDevConfigPath();
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("redis-dev", new FileSystemResource(configPath));
        Set<String> propertyNames = new HashSet<>();
        propertySources.stream()
                .filter(EnumerablePropertySource.class::isInstance)
                .map(EnumerablePropertySource.class::cast)
                .map(EnumerablePropertySource::getPropertyNames)
                .flatMap(Arrays::stream)
                .forEach(propertyNames::add);

        assertThat(propertyNames).contains(
                "payment.cache.redis.ttl[merchant:info]",
                "payment.cache.redis.ttl[merchant:openapi]",
                "payment.cache.redis.ttl[config:public]"
        );
    }

    /**
     * Spring Cache 的物理 Key 必须使用单冒号层级，避免默认双冒号造成命名歧义。
     */
    @Test
    void shouldBuildNormalizedCachePrefixAndPreserveTtl() {
        log.info("测试有限期缓存物理 Key，关键输入: acquiring:dev 前缀、30 分钟 TTL");
        RedisCacheConfiguration configuration = autoConfiguration.cacheConfiguration(
                Duration.ofMinutes(30),
                "acquiring:dev"
        );

        assertThat(configuration.getKeyPrefixFor("merchant:info"))
                .isEqualTo("acquiring:dev:merchant:info:");
        assertThat(configuration.getTtlFunction().getTimeToLive("merchant:info", "200045"))
                .isEqualTo(Duration.ofMinutes(30));
        log.info("有限期缓存物理 Key 测试完成，结果: 短前缀与 TTL 均符合约束");
    }

    /**
     * 配置项尾部存在多个冒号时也只能生成一个物理分隔符。
     */
    @Test
    void shouldCollapseTrailingSeparatorsInConfiguredPrefix() {
        log.info("测试 Spring Cache 前缀规范化，关键输入: 带空格和重复冒号的 acquiring:dev");
        RedisCacheConfiguration configuration = autoConfiguration.cacheConfiguration(
                Duration.ofMinutes(10),
                " acquiring:dev::: "
        );

        assertThat(configuration.getKeyPrefixFor("config:public"))
                .isEqualTo("acquiring:dev:config:public:");
        log.info("Spring Cache 前缀规范化测试完成，结果: 尾部仅保留一个分隔符");
    }

    /**
     * 开启 TTL 抖动后，每次写入的 TTL 必须位于基础 TTL 的有界区间内。
     */
    @Test
    void shouldApplyBoundedTtlJitter() {
        log.info("测试有限期缓存 TTL 抖动，关键输入: 10 分钟基础 TTL、10% 抖动");
        RedisCacheConfiguration configuration = autoConfiguration.cacheConfiguration(
                Duration.ofMinutes(10),
                "acquiring:dev",
                10
        );
        Set<Duration> observedTtls = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            Duration ttl = configuration.getTtlFunction()
                    .getTimeToLive("merchant-" + index, "value");
            assertThat(ttl).isBetween(Duration.ofMinutes(9), Duration.ofMinutes(11));
            observedTtls.add(ttl);
        }

        assertThat(observedTtls).hasSizeGreaterThan(1);
        log.info("有限期缓存 TTL 抖动测试完成，结果: 所有样本均位于 9 至 11 分钟");
    }

    /**
     * 已登记业务缓存必须保持常驻，且不能套用有限期缓存的 TTL 抖动。
     */
    @Test
    void shouldKeepRegisteredBusinessCachesPersistentWithoutJitter() {
        log.info("测试常驻业务缓存，关键输入: Registry 三类缓存、全局 10% TTL 抖动");
        PaymentCacheProperties properties = new PaymentCacheProperties();
        RedisCacheManager cacheManager = (RedisCacheManager) autoConfiguration.redisCacheManager(
                mock(RedisConnectionFactory.class),
                properties
        );
        cacheManager.afterPropertiesSet();
        TransactionAwareCacheDecorator cacheDecorator = (TransactionAwareCacheDecorator) cacheManager
                .getCache(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE);
        RedisCache redisCache = (RedisCache) cacheDecorator.getTargetCache();
        RedisCacheConfiguration configuration = redisCache.getCacheConfiguration();

        assertThat(properties.getTtlJitterPercent()).isEqualTo(10);
        assertThat(configuration.getTtl()).isZero();
        assertThat(configuration.getTtlFunction().getTimeToLive("200045", "value")).isZero();
        log.info("常驻业务缓存测试完成，结果: 物理 TTL 为零且未应用随机抖动");
    }

    /**
     * CacheManager 只能创建 Registry 已登记的 Cache，避免业务绕过 Catalog 动态扩展命名空间。
     */
    @Test
    void shouldRejectCacheNamesOutsideRegistry() {
        RedisCacheManager cacheManager = (RedisCacheManager) autoConfiguration.redisCacheManager(
                mock(RedisConnectionFactory.class),
                new PaymentCacheProperties()
        );
        cacheManager.afterPropertiesSet();

        assertThat(cacheManager.getCache(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE)).isNotNull();
        assertThat(cacheManager.getCache("unregistered:cache")).isNull();
    }

    /**
     * 风控时间线和未使用的 Spring Cache 声明必须退出 Registry，防止继续创建无收益缓存。
     */
    @Test
    void shouldNotRegisterRetiredOrUnusedCaches() {
        assertThat(PaymentCacheRegistry.defaultTtls())
                .doesNotContainKeys(
                        "risk:evaluation:detail",
                        "risk:runtime:rule"
                );
    }

    /**
     * TTL 覆盖配置不得注册 Catalog 之外的新 Cache Name。
     */
    @Test
    void shouldRejectTtlOverrideForUnregisteredCache() {
        PaymentCacheProperties properties = new PaymentCacheProperties();
        properties.setTtl(Map.of("unregistered:cache", Duration.ofMinutes(5)));

        assertThatIllegalArgumentException().isThrownBy(() -> autoConfiguration.redisCacheManager(
                mock(RedisConnectionFactory.class),
                properties
        ));
    }

    /**
     * 常驻 Cache 只允许显式配置零 TTL，有限期覆盖不能改变其生命周期语义。
     */
    @Test
    void shouldRejectPositiveOverrideForPersistentCache() {
        log.info("测试常驻缓存配置保护，关键输入: config:public 被覆盖为 5 分钟");
        PaymentCacheProperties properties = new PaymentCacheProperties();
        properties.setTtl(Map.of(PaymentCacheNames.PLATFORM_CONFIG, Duration.ofMinutes(5)));

        assertThatIllegalArgumentException().isThrownBy(() -> autoConfiguration.redisCacheManager(
                mock(RedisConnectionFactory.class),
                properties
        )).withMessageContaining("must remain zero");
        log.info("常驻缓存配置保护测试完成，结果: 有限期覆盖被拒绝");
    }

    /**
     * CacheManager 必须记录本地命中和未命中统计，供后续监控适配器读取。
     */
    @Test
    void shouldCollectLocalCacheStatistics() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands stringCommands = mock(RedisStringCommands.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.get(any(byte[].class))).thenReturn(null);
        RedisCacheManager cacheManager = (RedisCacheManager) autoConfiguration.redisCacheManager(
                connectionFactory,
                new PaymentCacheProperties()
        );
        cacheManager.afterPropertiesSet();
        TransactionAwareCacheDecorator cacheDecorator = (TransactionAwareCacheDecorator) cacheManager
                .getCache(PaymentCacheNames.PLATFORM_CONFIG);
        RedisCache redisCache = (RedisCache) cacheDecorator.getTargetCache();

        cacheDecorator.get("missing-config");

        assertThat(redisCache.getStatistics().getGets()).isEqualTo(1);
        assertThat(redisCache.getStatistics().getMisses()).isEqualTo(1);
        assertThat(redisCache.getStatistics().getHits()).isZero();
    }

    /**
     * TTL 抖动只允许关闭或使用 1~50 的有界百分比。
     */
    @Test
    void shouldRejectUnsafeTtlJitterConfiguration() {
        PaymentCacheProperties properties = new PaymentCacheProperties();

        properties.setTtlJitterPercent(0);
        assertThat(properties.getTtlJitterPercent()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTtlJitterPercent(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setTtlJitterPercent(51));
    }

    /**
     * 常驻缓存的失效门禁是恢复性租约，只要求为正数，不再与业务缓存 TTL 比较。
     */
    @Test
    void shouldAllowPositiveRecoveryGateForPersistentSecurityCache() {
        log.info("测试常驻安全缓存失效门禁，关键输入: 30 分钟恢复性租约");
        PaymentCacheProperties properties = new PaymentCacheProperties();
        properties.setInvalidationGateTtl(Duration.ofMinutes(30));

        assertThatCode(() ->
                autoConfiguration.redisCacheManager(
                        mock(RedisConnectionFactory.class),
                        properties
                )).doesNotThrowAnyException();
        log.info("常驻安全缓存失效门禁测试完成，结果: 正数恢复性租约允许配置");
    }

    /**
     * 普通读缓存故障继续回源，安全缓存删除失败必须向可靠失效链路暴露。
     */
    @Test
    void shouldFailOpenForReadsAndEscalateSecurityEvictionFailures() {
        Cache securityCache = mock(Cache.class);
        when(securityCache.getName()).thenReturn(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE);
        Cache ordinaryCache = mock(Cache.class);
        when(ordinaryCache.getName()).thenReturn(PaymentCacheNames.PLATFORM_CONFIG);
        CacheErrorHandler errorHandler = autoConfiguration.paymentCacheErrorHandler(
                RedisBusinessMetrics.noop()
        );
        RuntimeException redisFailure = new IllegalStateException("redis unavailable");

        assertThatCode(() -> errorHandler.handleCacheGetError(redisFailure, securityCache, "200045"))
                .doesNotThrowAnyException();
        assertThatCode(() -> errorHandler.handleCachePutError(
                redisFailure,
                securityCache,
                "200045",
                new Object()
        ))
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                errorHandler.handleCacheEvictError(redisFailure, securityCache, "200045"))
                .isSameAs(redisFailure);
        assertThatThrownBy(() ->
                errorHandler.handleCacheClearError(redisFailure, securityCache))
                .isSameAs(redisFailure);
        assertThatCode(() ->
                errorHandler.handleCacheEvictError(redisFailure, ordinaryCache, "system.name"))
                .doesNotThrowAnyException();
    }

    private Path redisDevConfigPath() {
        Path repositoryPath = Path.of("docs", "deployment", "nacos", "redis-dev.yaml");
        if (Files.isRegularFile(repositoryPath)) {
            return repositoryPath;
        }
        Path modulePath = Path.of("..", "..", "docs", "deployment", "nacos", "redis-dev.yaml");
        assertThat(Files.isRegularFile(modulePath))
                .as("Redis dev Nacos configuration must exist")
                .isTrue();
        return modulePath;
    }

    /**
     * Spring Cache Value 必须使用集中定义的受控反序列化边界。
     */
    @Test
    void shouldApplyRestrictedSerializerToSpringCache() {
        RedisCacheConfiguration configuration = autoConfiguration.cacheConfiguration(
                Duration.ofMinutes(10),
                "acquiring:test"
        );
        byte[] untrustedValue = currentSerializer().serialize(new File("/tmp/payment-redis"));

        assertThatThrownBy(() -> configuration.getValueSerializationPair()
                        .read(ByteBuffer.wrap(untrustedValue)))
                .isInstanceOf(SerializationException.class);
    }

    private GenericJackson2JsonRedisSerializer currentSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
