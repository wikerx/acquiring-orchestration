package com.scott.payment.component.redis.idempotent;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.idempotent.impl.RedisIdempotentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisIdempotentIntegrationTests
 * @date : 2026-07-30 19:00
 * @email : scott_x@163.com
 * @description : 在显式启用时使用真实 Redis 验证 MQ 双桶去重、容量降级、失败释放和跨桶重复判断
 * @status : create
 */
@Slf4j
@EnabledIfSystemProperty(named = "idempotent.redis.integration.enabled", matches = "true")
class RedisIdempotentIntegrationTests {

    /**
     * 测试专用 Lettuce 连接工厂；连接参数来自系统属性，密码不进入日志。
     */
    private LettuceConnectionFactory connectionFactory;

    /**
     * 测试专用字符串 Redis 模板，用于执行生产 Lua 和精确删除测试 Key。
     */
    private StringRedisTemplate redisTemplate;

    /**
     * 随机环境前缀下的 Redis Key 配置，避免并行测试与其他环境互相覆盖。
     */
    private PaymentRedisProperties redisProperties;

    /**
     * 当前测试实例使用的低基数 MQ 命名空间，不包含真实消息或商户标识。
     */
    private String namespace;

    /**
     * 当前测试计算过的物理 Key；清理只删除该集合，不执行 KEYS 或模式删除。
     */
    private final Set<String> cleanupKeys = new LinkedHashSet<>();

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getProperty("idempotent.redis.host", "127.0.0.1"),
                Integer.parseInt(System.getProperty("idempotent.redis.port", "6379"))
        );
        configuration.setDatabase(Integer.parseInt(System.getProperty("idempotent.redis.database", "0")));
        String password = System.getProperty("idempotent.redis.password", "");
        if (!password.isBlank()) {
            configuration.setPassword(password);
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        String isolation = UUID.randomUUID().toString().replace("-", "");
        redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:it-" + isolation);
        redisProperties.getMqDedup().setMaxMembersPerBucket(2);
        namespace = "mq-it-" + isolation;
    }

    @AfterEach
    void tearDown() {
        rememberCandidateKeys(60L);
        rememberCandidateKeys(2L);
        if (redisTemplate != null && !cleanupKeys.isEmpty()) {
            redisTemplate.delete(cleanupKeys);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldAcquireDetectDuplicateReleaseAndAcquireAgain() {
        log.info("测试真实 Redis MQ 去重生命周期，关键输入: 同一摘要重复获取、失败释放后再次获取");
        RedisIdempotentServiceImpl service = service();
        rememberCandidateKeys(60L);

        IdempotentAcquireResult first = service.acquireMq(namespace, "MESSAGE-001", 60L);
        IdempotentAcquireResult duplicate = service.acquireMq(namespace, "MESSAGE-001", 60L);
        service.releaseMq(namespace, "MESSAGE-001", 60L);
        IdempotentAcquireResult afterRelease = service.acquireMq(namespace, "MESSAGE-001", 60L);

        assertThat(first).isEqualTo(IdempotentAcquireResult.ACQUIRED);
        assertThat(duplicate).isEqualTo(IdempotentAcquireResult.DUPLICATE);
        assertThat(afterRelease).isEqualTo(IdempotentAcquireResult.ACQUIRED);
        log.info("真实 Redis MQ 去重生命周期测试完成，结果: ACQUIRED/DUPLICATE/ACQUIRED");
    }

    @Test
    void shouldFallbackAtCapacityWhileKeepingExistingDuplicateVisible() {
        log.info("测试真实 Redis MQ 容量边界，关键输入: 单桶上限 2、第三个不同摘要");
        RedisIdempotentServiceImpl service = service();
        rememberCandidateKeys(60L);

        assertThat(service.acquireMq(namespace, "MESSAGE-A", 60L))
                .isEqualTo(IdempotentAcquireResult.ACQUIRED);
        assertThat(service.acquireMq(namespace, "MESSAGE-B", 60L))
                .isEqualTo(IdempotentAcquireResult.ACQUIRED);
        assertThat(service.acquireMq(namespace, "MESSAGE-C", 60L))
                .isEqualTo(IdempotentAcquireResult.FALLBACK);
        assertThat(service.acquireMq(namespace, "MESSAGE-A", 60L))
                .isEqualTo(IdempotentAcquireResult.DUPLICATE);
        log.info("真实 Redis MQ 容量边界测试完成，结果: 新摘要降级且既有重复仍可识别");
    }

    @Test
    void shouldDetectDuplicateAcrossAdjacentBuckets() throws InterruptedException {
        log.info("测试真实 Redis MQ 跨桶边界，关键输入: 2 秒 TTL、首次写入发生在桶切换前");
        RedisIdempotentServiceImpl service = service();
        waitUntilShortlyBeforeBucketBoundary(2_000L);
        rememberCandidateKeys(2L);

        IdempotentAcquireResult first = service.acquireMq(namespace, "MESSAGE-BOUNDARY", 2L);
        TimeUnit.MILLISECONDS.sleep(250L);
        rememberCandidateKeys(2L);
        IdempotentAcquireResult duplicate = service.acquireMq(namespace, "MESSAGE-BOUNDARY", 2L);

        assertThat(first).isEqualTo(IdempotentAcquireResult.ACQUIRED);
        assertThat(duplicate).isEqualTo(IdempotentAcquireResult.DUPLICATE);
        log.info("真实 Redis MQ 跨桶边界测试完成，结果: 前一桶摘要在有效窗口内仍判定重复");
    }

    /**
     * 在本机临时 Redis 上采样生产 MQ 双桶 Lua 的顺序吞吐和尾延迟。
     *
     * <p>该样本不包含 RocketMQ、数据库唯一约束和生产网络，不作为生产容量结论。</p>
     */
    @Test
    void shouldMeasureMqDedupLatencyWithRealRedis() {
        int warmupCount = 100;
        int measuredCount = 2_000;
        redisProperties.getMqDedup().setMaxMembersPerBucket(warmupCount + measuredCount);
        RedisIdempotentServiceImpl service = service();
        rememberCandidateKeys(60L);
        long[] latencyNanos = new long[measuredCount];

        for (int index = 0; index < warmupCount; index++) {
            assertThat(service.acquireMq(namespace, "WARMUP-" + index, 60L))
                    .isEqualTo(IdempotentAcquireResult.ACQUIRED);
        }

        long benchmarkStartedNanos = System.nanoTime();
        for (int index = 0; index < measuredCount; index++) {
            long operationStartedNanos = System.nanoTime();
            IdempotentAcquireResult result =
                    service.acquireMq(namespace, "MEASURED-" + index, 60L);
            latencyNanos[index] = System.nanoTime() - operationStartedNanos;
            assertThat(result).isEqualTo(IdempotentAcquireResult.ACQUIRED);
        }
        long elapsedNanos = System.nanoTime() - benchmarkStartedNanos;

        log.info(
                "真实 Redis MQ 去重基础性能完成，样本数: {} throughputOpsPerSecond: {} "
                        + "p95Micros: {} p99Micros: {}；仅代表本机临时容器顺序调用",
                measuredCount,
                throughputPerSecond(measuredCount, elapsedNanos),
                percentileMicros(latencyNanos, 0.95D),
                percentileMicros(latencyNanos, 0.99D)
        );
    }

    /**
     * 创建使用真实 Redis 模板和当前测试边界配置的幂等服务。
     *
     * @return MQ 辅助幂等服务
     */
    private RedisIdempotentServiceImpl service() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", redisTemplate);
        ObjectProvider<StringRedisTemplate> provider = beanFactory.getBeanProvider(StringRedisTemplate.class);
        return new RedisIdempotentServiceImpl(provider, redisProperties);
    }

    /**
     * 等待到当前 Redis 时间距离下一桶边界约 100 毫秒，稳定覆盖前后两个时间桶。
     *
     * @param bucketMillis 桶宽，单位毫秒
     * @throws InterruptedException 当前测试线程被中断
     */
    private void waitUntilShortlyBeforeBucketBoundary(long bucketMillis) throws InterruptedException {
        long nowMillis = redisCurrentTimeMillis();
        long waitMillis = bucketMillis - Math.floorMod(nowMillis, bucketMillis) - 100L;
        if (waitMillis > 0L) {
            TimeUnit.MILLISECONDS.sleep(waitMillis);
        }
    }

    /**
     * 记录当前桶及其相邻桶的精确 Key，覆盖测试期间可能发生的一次边界切换。
     *
     * @param ttlSeconds 测试去重 TTL，单位秒
     */
    private void rememberCandidateKeys(long ttlSeconds) {
        if (redisTemplate == null || redisProperties == null || namespace == null) {
            return;
        }
        long bucketMillis = ttlSeconds * 1_000L;
        long currentBucket = Math.floorDiv(redisCurrentTimeMillis(), bucketMillis);
        for (long bucket = currentBucket - 2L; bucket <= currentBucket + 1L; bucket++) {
            cleanupKeys.add(redisProperties.coLocatedBusinessKey(
                    "mq", "dedup", namespace, namespace, String.valueOf(bucket)));
        }
    }

    /**
     * 获取真实 Redis 服务端毫秒时间。
     *
     * @return Redis epochMillis
     */
    private long redisCurrentTimeMillis() {
        Long currentMillis = redisTemplate.execute(
                (RedisCallback<Long>) connection -> connection.serverCommands().time(TimeUnit.MILLISECONDS)
        );
        if (currentMillis == null) {
            throw new IllegalStateException("Redis TIME returned null in integration test");
        }
        return currentMillis;
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
