package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdConstants;
import com.scott.payment.component.core.id.GlobalIdValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdIntegrationTests
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : 在显式启用时验证真实 Redis TIME、状态 Hash、连续与并发发号唯一性以及序列溢出
 * @status : create
 */
@Slf4j
@EnabledIfSystemProperty(named = "global-id.redis.integration.enabled", matches = "true")
class RedisGlobalIdIntegrationTests {

    /**
     * 测试专用 Lettuce 连接工厂；只连接系统属性指定的 Redis，不包含密码日志。
     */
    private LettuceConnectionFactory connectionFactory;

    /**
     * 测试专用字符串 Redis 模板，用于执行 Lua 并核对测试状态 Key。
     */
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 每个测试实例独享的全局 ID 配置，环境片段随机生成，避免并行测试互相覆盖。
     */
    private RedisGlobalIdProperties properties;

    @BeforeEach
    void setUp() {
        String configuredNodes = System.getProperty(
                "global-id.redis.cluster.nodes",
                "127.0.0.1:7001,127.0.0.1:7002,127.0.0.1:7003,"
                        + "127.0.0.1:7004,127.0.0.1:7005,127.0.0.1:7006"
        );
        String password = System.getProperty("global-id.redis.cluster.password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "global-id.redis.cluster.password is required for Cluster integration tests");
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

        stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        properties = new RedisGlobalIdProperties();
        properties.setStateKey(
                "acquiring:it-" + UUID.randomUUID().toString().replace("-", "") + ":global-id:state");
        properties.setRetrySleepMillis(1L);
        cleanIntegrationKeys();
    }

    @AfterEach
    void tearDown() {
        cleanIntegrationKeys();
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void redisConnectionShouldExposeServerTime() {
        log.info("测试真实 Redis TIME，关键输入: 集成测试连接配置");
        RedisServerTimeProvider timeProvider = new RedisServerTimeProvider(stringRedisTemplate);

        long currentMillis = timeProvider.currentTimeMillis();

        assertThat(currentMillis).isPositive();
        log.info("真实 Redis TIME 测试完成，结果: 返回正数 epochMillis");
    }

    @Test
    void nextIdShouldUseRealRedisAndWriteKeys() {
        log.info("测试真实 Redis 全局 ID 状态，关键输入: 随机隔离 state Key");
        RedisGlobalIdGenerator generator = newGenerator();

        String id = generator.nextId();

        assertThat(id).hasSize(GlobalIdConstants.ID_LENGTH);
        assertThat(id).containsOnlyDigits();
        assertThat(GlobalIdValidator.isValid(id)).isTrue();
        assertThat(stringRedisTemplate.hasKey(properties.getStateKey())).isTrue();
        assertThat(stringRedisTemplate.opsForHash().get(properties.getStateKey(), "last_millis")).isNotNull();
        assertThat(stringRedisTemplate.opsForHash().get(properties.getStateKey(), "sequence")).isNotNull();
        log.info("真实 Redis 全局 ID 状态测试完成，结果: Hash 两个状态字段均已写入");
    }

    @Test
    void nextIdShouldNotDuplicateWhenCalledContinuouslyWithRealRedis() {
        log.info("测试真实 Redis 连续发号，关键输入: 单线程 10000 次");
        RedisGlobalIdGenerator generator = newGenerator();
        Set<String> ids = ConcurrentHashMap.newKeySet();
        int measuredCount = 10_000;
        long[] latencyNanos = new long[measuredCount];

        long benchmarkStartedNanos = System.nanoTime();
        for (int index = 0; index < measuredCount; index++) {
            long operationStartedNanos = System.nanoTime();
            String id = generator.nextId();
            latencyNanos[index] = System.nanoTime() - operationStartedNanos;
            assertThat(GlobalIdValidator.isValid(id)).isTrue();
            ids.add(id);
        }
        long elapsedNanos = System.nanoTime() - benchmarkStartedNanos;

        assertThat(ids).hasSize(measuredCount);
        log.info(
                "真实 Redis 连续发号测试完成，结果: {} 个编号全部唯一 throughputOpsPerSecond: {} "
                        + "p95Micros: {} p99Micros: {}；仅代表本机临时容器顺序调用",
                measuredCount,
                throughputPerSecond(measuredCount, elapsedNanos),
                percentileMicros(latencyNanos, 0.95D),
                percentileMicros(latencyNanos, 0.99D)
        );
    }

    @Test
    void nextIdShouldNotDuplicateWhenCalledConcurrentlyWithRealRedis() throws InterruptedException {
        log.info("测试真实 Redis 并发发号，关键输入: 20 线程、每线程 5000 次");
        RedisGlobalIdGenerator generator = newGenerator();
        int threadCount = 20;
        int perThreadCount = 5_000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        List<Throwable> failures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int index = 0; index < perThreadCount; index++) {
                        String id = generator.nextId();
                        if (!GlobalIdValidator.isValid(id)) {
                            failures.add(new AssertionError("invalid global id: " + id));
                        }
                        ids.add(id);
                    }
                } catch (Throwable throwable) {
                    failures.add(throwable);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        assertThat(finishLatch.await(60, TimeUnit.SECONDS)).isTrue();
        executorService.shutdownNow();
        assertThat(failures).isEmpty();
        assertThat(ids).hasSize(threadCount * perThreadCount);
        log.info("真实 Redis 并发发号测试完成，结果: {} 个编号全部唯一", ids.size());
    }

    @Test
    void nextIdShouldFailWhenFutureMillisSequenceAlreadyOverflowed() {
        log.info("测试真实 Redis 未来状态溢出，关键输入: last_millis 超前 60 秒、序列已满");
        long futureMillis = new RedisServerTimeProvider(stringRedisTemplate).currentTimeMillis() + 60_000L;
        stringRedisTemplate.opsForHash().put(properties.getStateKey(), "last_millis", String.valueOf(futureMillis));
        stringRedisTemplate.opsForHash().put(properties.getStateKey(), "sequence", String.valueOf(properties.getMaxSequence()));
        properties.setMaxRetryTimes(0);
        RedisGlobalIdGenerator generator = newGenerator();

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("全局唯一标识序列超过毫秒上限");
        log.info("真实 Redis 未来状态溢出测试完成，结果: 未生成重复编号");
    }

    private RedisGlobalIdGenerator newGenerator() {
        return new RedisGlobalIdGenerator(
                stringRedisTemplate,
                new RedisServerTimeProvider(stringRedisTemplate),
                properties
        );
    }

    private void cleanIntegrationKeys() {
        if (stringRedisTemplate == null) {
            return;
        }
        if (properties != null && properties.getStateKey() != null) {
            stringRedisTemplate.delete(properties.getStateKey());
        }
    }

    /**
     * 计算指定样本在总耗时内的整数吞吐，单位为操作数/秒。
     *
     * @param operationCount 操作数量
     * @param elapsedNanos   总耗时，单位纳秒
     * @return 每秒完成操作数
     */
    private long throughputPerSecond(int operationCount, long elapsedNanos) {
        return Math.round(operationCount * 1_000_000_000D / Math.max(1L, elapsedNanos));
    }

    /**
     * 计算单次调用耗时样本的最近秩百分位，返回微秒便于报告阅读。
     *
     * @param latencyNanos 纳秒耗时样本
     * @param percentile  百分位，取值范围 0 到 1
     * @return 对应百分位耗时，单位微秒
     */
    private long percentileMicros(long[] latencyNanos, double percentile) {
        long[] sorted = latencyNanos.clone();
        Arrays.sort(sorted);
        int index = Math.max(0, Math.min(
                sorted.length - 1,
                (int) Math.ceil(percentile * sorted.length) - 1
        ));
        return TimeUnit.NANOSECONDS.toMicros(sorted[index]);
    }
}
