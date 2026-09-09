package com.scott.payment.component.redis.generation;

import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.redis.cache.invalidation.RedisCacheInvalidationGuard;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheGenerationClusterIntegrationTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 在真实 Redis Cluster 上验证缓存代际多 Key Lua 和 token 租约释放语义。
 * @status : create
 */
@EnabledIfSystemProperty(named = "cache-generation.redis.cluster.integration.enabled", matches = "true")
class RedisCacheGenerationClusterIntegrationTests {

    /** 当前用例创建的 Redis Key，测试结束时逐项清理。 */
    private final Set<String> cleanupKeys = new LinkedHashSet<>();

    /** 真实 Redis Cluster 测试使用的 Lettuce 连接工厂。 */
    private LettuceConnectionFactory connectionFactory;
    /** 对真实 Cluster 执行代际 Lua 的字符串模板。 */
    private StringRedisTemplate redisTemplate;
    /** 使用随机前缀隔离本次运行的 Redis 业务 Key。 */
    private PaymentRedisProperties redisProperties;

    @BeforeEach
    void setUp() {
        String configuredNodes = System.getProperty(
                "cache-generation.redis.cluster.nodes",
                "127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,"
                        + "127.0.0.1:7004,127.0.0.1:7005,127.0.0.1:7006"
        );
        String password = System.getProperty("cache-generation.redis.cluster.password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "cache-generation.redis.cluster.password is required for Cluster integration tests");
        }

        RedisClusterConfiguration configuration = new RedisClusterConfiguration(
                Arrays.stream(configuredNodes.split(","))
                        .map(String::trim)
                        .filter(node -> !node.isEmpty())
                        .toList()
        );
        configuration.setMaxRedirects(5);
        configuration.setPassword(RedisPassword.of(password));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:it-" + UUID.randomUUID().toString().replace("-", ""));
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            cleanupKeys.forEach(redisTemplate::delete);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldReadPublishAndCommitGenerationInOneClusterSlot() {
        String namespace = "risk-runtime-rule";
        rememberGenerationKeys(namespace);
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(redisTemplate, redisProperties);

        RedisCacheGenerationState initial = store.current(namespace);
        RedisCachePublication publication = store.begin(namespace, Duration.ofSeconds(10));

        assertThat(initial.cacheReadable()).isTrue();
        assertThat(store.current(namespace).cacheReadable()).isFalse();
        assertThat(store.commit(publication)).isTrue();
        assertThat(store.commit(publication)).isTrue();
        assertThat(store.current(namespace).generation()).isEqualTo(publication.generation());
    }

    @Test
    void shouldReleaseOnlyTheCurrentCacheInvalidationLeaseOwner() {
        String businessKey = "merchant-" + UUID.randomUUID().toString().replace("-", "");
        cleanupKeys.add(redisProperties.businessKey("merchant", "info", "pending", businessKey));
        RedisCacheInvalidationGuard guard = new RedisCacheInvalidationGuard(redisTemplate, redisProperties);

        CacheInvalidationLease lease = guard.acquire(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                businessKey,
                Duration.ofSeconds(10)
        );
        CacheInvalidationLease foreignLease = new CacheInvalidationLease(
                lease.cacheName(), lease.businessKey(), "foreign-token");

        assertThat(guard.isPending(lease.cacheName(), lease.businessKey())).isTrue();
        assertThat(guard.release(foreignLease)).isFalse();
        assertThat(guard.isPending(lease.cacheName(), lease.businessKey())).isTrue();
        assertThat(guard.release(lease)).isTrue();
        assertThat(guard.isPending(lease.cacheName(), lease.businessKey())).isFalse();
    }

    private void rememberGenerationKeys(String namespace) {
        cleanupKeys.add(redisProperties.coLocatedBusinessKey(
                "cache", "generation", namespace, "current"));
        cleanupKeys.add(redisProperties.coLocatedBusinessKey(
                "cache", "generation", namespace, "publication"));
    }
}
