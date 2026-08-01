package com.scott.payment.component.redis.config;

import com.scott.payment.component.redis.lock.DistributedLockService;
import com.scott.payment.component.redis.lock.impl.RedissonDistributedLockService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用两个真实 Redisson Cluster 客户端验证统一锁的跨客户端互斥、可重入、租约和持有者保护。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "redisson.redis.cluster.integration.enabled", matches = "true")
class RedissonDistributedLockClusterIntegrationTests {

    private RedissonClient firstClient;
    private RedissonClient secondClient;
    private DistributedLockService firstLockService;
    private DistributedLockService secondLockService;
    private String keyPrefix;

    /**
     * 从显式系统属性创建两个独立客户端，模拟两个服务实例竞争同一把锁。
     */
    @BeforeAll
    void setUpClients() {
        String configuredNodes = System.getProperty(
                "redisson.redis.cluster.nodes",
                "127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,"
                        + "127.0.0.1:7004,127.0.0.1:7005,127.0.0.1:7006"
        );
        String password = System.getProperty("redisson.redis.cluster.password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "redisson.redis.cluster.password is required for Cluster integration tests");
        }
        RedisProperties redisProperties = new RedisProperties();
        RedisProperties.Cluster cluster = new RedisProperties.Cluster();
        cluster.setNodes(Arrays.stream(configuredNodes.split(","))
                .map(String::trim)
                .filter(node -> !node.isEmpty())
                .toList());
        cluster.setMaxRedirects(5);
        redisProperties.setCluster(cluster);
        redisProperties.setPassword(password);
        redisProperties.setConnectTimeout(Duration.ofSeconds(3));
        redisProperties.setTimeout(Duration.ofSeconds(5));

        PaymentRedissonProperties redissonProperties = new PaymentRedissonProperties();
        redissonProperties.setMasterConnectionMinimumIdleSize(1);
        redissonProperties.setMasterConnectionPoolSize(4);
        redissonProperties.setSlaveConnectionMinimumIdleSize(0);
        redissonProperties.setSlaveConnectionPoolSize(2);
        redissonProperties.setSubscriptionConnectionMinimumIdleSize(0);
        redissonProperties.setSubscriptionConnectionPoolSize(1);
        redissonProperties.setLockWatchdogTimeout(Duration.ofSeconds(1));
        firstClient = Redisson.create(PaymentRedisClusterAutoConfiguration.buildRedissonConfig(
                redisProperties, redissonProperties));
        secondClient = Redisson.create(PaymentRedisClusterAutoConfiguration.buildRedissonConfig(
                redisProperties, redissonProperties));
        firstLockService = new RedissonDistributedLockService(firstClient);
        secondLockService = new RedissonDistributedLockService(secondClient);
        keyPrefix = "acquiring:cluster-it:lock:{"
                + UUID.randomUUID().toString().replace("-", "") + "}";
    }

    /**
     * 关闭测试专用客户端，避免测试完成后保留 Netty 线程和 Redis 连接。
     */
    @AfterAll
    void closeClients() {
        if (firstClient != null) {
            firstClient.shutdown();
        }
        if (secondClient != null) {
            secondClient.shutdown();
        }
    }

    /**
     * 验证一个客户端持锁期间另一客户端快速失败，释放后才能进入临界区。
     */
    @Test
    void shouldEnforceMutualExclusionAcrossClients() throws Exception {
        String key = keyPrefix + ":mutual";
        assertThat(firstLockService.tryLock(key, Duration.ZERO, Duration.ofSeconds(5))).isTrue();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> contended = executor.submit(() -> secondLockService.tryLock(
                    key, Duration.ZERO, Duration.ofSeconds(5)));
            assertThat(contended.get()).isFalse();

            secondLockService.unlock(key);
            assertThat(firstLockService.isHeldByCurrentThread(key)).isTrue();
            firstLockService.unlock(key);

            Future<Boolean> acquiredAfterRelease = executor.submit(() -> {
                boolean acquired = secondLockService.tryLock(key, Duration.ZERO, Duration.ofSeconds(5));
                if (acquired) {
                    secondLockService.unlock(key);
                }
                return acquired;
            });
            assertThat(acquiredAfterRelease.get()).isTrue();
        } finally {
            firstLockService.unlock(key);
            executor.shutdownNow();
        }
    }

    /**
     * 验证同一客户端线程可重入，且必须按重入次数完整释放。
     */
    @Test
    void shouldSupportReentrantLocking() {
        String key = keyPrefix + ":reentrant";
        assertThat(firstLockService.tryLock(key, Duration.ZERO, Duration.ofSeconds(5))).isTrue();
        assertThat(firstLockService.tryLock(key, Duration.ZERO, Duration.ofSeconds(5))).isTrue();

        firstLockService.unlock(key);
        assertThat(firstLockService.isHeldByCurrentThread(key)).isTrue();
        firstLockService.unlock(key);
        assertThat(firstLockService.isHeldByCurrentThread(key)).isFalse();
    }

    /**
     * 验证固定租约到期后其他客户端可重新获取，不依赖永久 watchdog 续期。
     */
    @Test
    void shouldReleaseAfterFixedLeaseExpires() throws InterruptedException {
        String key = keyPrefix + ":lease";
        assertThat(firstLockService.tryLock(key, Duration.ZERO, Duration.ofMillis(500))).isTrue();
        assertThat(secondLockService.tryLock(key, Duration.ZERO, Duration.ofSeconds(2))).isFalse();

        boolean acquired = false;
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!acquired && System.nanoTime() < deadlineNanos) {
            Thread.sleep(100L);
            acquired = secondLockService.tryLock(key, Duration.ZERO, Duration.ofSeconds(2));
        }

        assertThat(acquired).isTrue();
        secondLockService.unlock(key);
        firstLockService.unlock(key);
    }

    /**
     * 验证 watchdog 在持有者存活时持续续期，并在显式解锁后允许其他客户端获取。
     */
    @Test
    void shouldRenewWatchdogLockUntilOwnerUnlocks() throws InterruptedException {
        String key = keyPrefix + ":watchdog";
        assertThat(firstLockService.tryLockWithWatchdog(key, Duration.ZERO)).isTrue();

        Thread.sleep(1_600L);

        assertThat(secondLockService.tryLock(key, Duration.ZERO, Duration.ofSeconds(2))).isFalse();
        firstLockService.unlock(key);
        assertThat(secondLockService.tryLock(key, Duration.ZERO, Duration.ofSeconds(2))).isTrue();
        secondLockService.unlock(key);
    }
}
