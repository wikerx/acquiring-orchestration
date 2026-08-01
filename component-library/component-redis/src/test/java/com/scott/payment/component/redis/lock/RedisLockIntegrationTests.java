package com.scott.payment.component.redis.lock;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.lock.impl.RedisLockServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
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
 * @classname : RedisLockIntegrationTests
 * @date : 2026-07-30 22:30
 * @email : scott_x@163.com
 * @description : 在显式启用时使用真实 Redis 验证锁持有者释放、租约超时恢复和基础延迟
 * @status : create
 */
@Slf4j
@EnabledIfSystemProperty(named = "lock.redis.integration.enabled", matches = "true")
class RedisLockIntegrationTests {

    /**
     * 测试专用 Lettuce 连接工厂；连接参数来自系统属性，密码不进入日志。
     */
    private LettuceConnectionFactory connectionFactory;

    /**
     * 测试专用字符串 Redis 模板，用于执行生产锁操作和精确清理测试 Key。
     */
    private StringRedisTemplate redisTemplate;

    /**
     * 当前测试连接的真实 Redis 锁服务。
     */
    private RedisLockServiceImpl lockService;

    /**
     * 当前测试计算过的物理 Key；清理只删除该集合，不执行模式扫描。
     */
    private final Set<String> cleanupKeys = new LinkedHashSet<>();

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getProperty("lock.redis.host", "127.0.0.1"),
                Integer.parseInt(System.getProperty("lock.redis.port", "6379"))
        );
        configuration.setDatabase(Integer.parseInt(System.getProperty("lock.redis.database", "0")));
        String password = System.getProperty("lock.redis.password", "");
        if (!password.isBlank()) {
            configuration.setPassword(password);
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        lockService = new RedisLockServiceImpl(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null && !cleanupKeys.isEmpty()) {
            redisTemplate.delete(cleanupKeys);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /**
     * 验证非持有者 token 不能释放锁，原持有者释放后竞争者才能获取。
     */
    @Test
    void shouldOnlyReleaseLockForCurrentHolder() {
        log.info("测试真实 Redis 锁持有者边界，关键输入: holder-A 获取、holder-B 尝试释放");
        String lockKey = uniqueLockKey("holder");

        assertThat(lockService.tryLock(lockKey, "holder-A", 5L)).isTrue();
        assertThat(lockService.tryLock(lockKey, "holder-B", 5L)).isFalse();

        lockService.unlock(lockKey, "holder-B");
        assertThat(redisTemplate.opsForValue().get(lockKey)).isEqualTo("holder-A");
        assertThat(lockService.tryLock(lockKey, "holder-B", 5L)).isFalse();

        lockService.unlock(lockKey, "holder-A");
        assertThat(lockService.tryLock(lockKey, "holder-B", 5L)).isTrue();
        log.info("真实 Redis 锁持有者测试完成，结果: 非持有者未删除锁，持有者释放后可重新获取");
    }

    /**
     * 验证持有者未主动释放时，短租约到期后竞争者可以恢复获取。
     *
     * @throws InterruptedException 当前测试线程被中断
     */
    @Test
    void shouldRecoverAfterLockLeaseExpires() throws InterruptedException {
        log.info("测试真实 Redis 锁租约边界，关键输入: 1 秒 TTL、持有者不主动释放");
        String lockKey = uniqueLockKey("ttl");
        assertThat(lockService.tryLock(lockKey, "expired-holder", 1L)).isTrue();

        boolean acquiredAfterExpiry = false;
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L);
        while (!acquiredAfterExpiry && System.nanoTime() < deadlineNanos) {
            acquiredAfterExpiry = lockService.tryLock(lockKey, "next-holder", 5L);
            if (!acquiredAfterExpiry) {
                TimeUnit.MILLISECONDS.sleep(50L);
            }
        }

        assertThat(acquiredAfterExpiry).isTrue();
        assertThat(redisTemplate.opsForValue().get(lockKey)).isEqualTo("next-holder");
        log.info("真实 Redis 锁租约测试完成，结果: 过期后竞争者在 3 秒观察窗口内获取成功");
    }

    /**
     * 在本机临时 Redis 上采样获取与安全释放组合路径的顺序吞吐和尾延迟。
     *
     * <p>该样本不包含业务事务、进程调度和生产网络，不作为生产锁容量结论。</p>
     */
    @Test
    void shouldMeasureLockAcquireAndReleaseLatencyWithRealRedis() {
        int measuredCount = 2_000;
        String lockKey = uniqueLockKey("performance");
        long[] latencyNanos = new long[measuredCount];
        long benchmarkStartedNanos = System.nanoTime();

        for (int index = 0; index < measuredCount; index++) {
            String holder = "holder-" + index;
            long operationStartedNanos = System.nanoTime();
            assertThat(lockService.tryLock(lockKey, holder, 5L)).isTrue();
            lockService.unlock(lockKey, holder);
            latencyNanos[index] = System.nanoTime() - operationStartedNanos;
        }
        long elapsedNanos = System.nanoTime() - benchmarkStartedNanos;

        assertThat(redisTemplate.hasKey(lockKey)).isFalse();
        log.info(
                "真实 Redis 锁基础性能完成，样本数: {} throughputOpsPerSecond: {} "
                        + "p95Micros: {} p99Micros: {}；每个样本包含 SET NX PX 与 token compare-delete Lua",
                measuredCount,
                throughputPerSecond(measuredCount, elapsedNanos),
                percentileMicros(latencyNanos, 0.95D),
                percentileMicros(latencyNanos, 0.99D)
        );
    }

    /**
     * 创建符合项目精简规范的随机隔离锁 Key，并登记为精确清理目标。
     *
     * @param purpose 测试用途片段
     * @return 测试专用物理 Key
     */
    private String uniqueLockKey(String purpose) {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:it-" + UUID.randomUUID().toString().replace("-", ""));
        String lockKey = properties.businessKey("payment", "lock", purpose);
        cleanupKeys.add(lockKey);
        return lockKey;
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
