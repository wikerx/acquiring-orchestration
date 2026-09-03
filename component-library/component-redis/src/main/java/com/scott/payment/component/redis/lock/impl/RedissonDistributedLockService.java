package com.scott.payment.component.redis.lock.impl;

import com.scott.payment.component.redis.lock.DistributedLockBusyException;
import com.scott.payment.component.redis.lock.DistributedLockExecution;
import com.scott.payment.component.redis.lock.DistributedLockInterruptedException;
import com.scott.payment.component.redis.lock.DistributedLockService;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedissonDistributedLockService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 基于单个 Spring 管理的 RedissonClient 提供有界、可重入的分布式锁能力。
 * @status : create
 */
public class RedissonDistributedLockService implements DistributedLockService {

    /** Spring 管理的共享 Redisson 客户端；本服务不负责关闭其生命周期。 */
    private final RedissonClient redissonClient;
    /** 锁获取、竞争、失败和释放结果的低基数指标记录器。 */
    private final RedisBusinessMetrics metrics;

    /**
     * 使用空操作指标创建锁服务，供无需指标装配的测试或轻量调用方使用。
     *
     * @param redissonClient Spring 管理的 Redisson 客户端
     */
    public RedissonDistributedLockService(RedissonClient redissonClient) {
        this(redissonClient, RedisBusinessMetrics.noop());
    }

    /**
     * 创建带 Redis 业务指标的统一锁服务。
     *
     * @param redissonClient Spring 管理的 Redisson 客户端
     * @param metrics 锁操作指标记录器；为空时退化为空操作实现
     */
    public RedissonDistributedLockService(RedissonClient redissonClient,
                                          RedisBusinessMetrics metrics) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient");
        this.metrics = metrics == null ? RedisBusinessMetrics.noop() : metrics;
    }

    /**
     * 在限定时间内获取带固定租约的可重入锁，并保留线程中断语义。
     *
     * @param key 完整 Redis 锁 Key，不允许为空
     * @param waitTime 最大等待时间，允许为零
     * @param leaseTime 固定锁租约，必须大于零
     * @return 获取成功返回 true，锁竞争超时返回 false
     */
    @Override
    public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
        RLock lock = lock(key);
        long waitMillis = waitMillis(waitTime);
        long leaseMillis = positiveMillis(leaseTime, "leaseTime");
        long startNanos = System.nanoTime();
        try {
            boolean acquired = lock.tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
            recordAcquire(acquired, startNanos);
            return acquired;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordAcquireError(startNanos);
            throw new DistributedLockInterruptedException("Interrupted while acquiring distributed lock", exception);
        } catch (RuntimeException exception) {
            recordAcquireError(startNanos);
            throw exception;
        }
    }

    /**
     * 在限定时间内获取由 Redisson 看门狗续期的可重入锁。
     *
     * @param key 完整 Redis 锁 Key，不允许为空
     * @param waitTime 最大等待时间，允许为零
     * @return 获取成功返回 true，锁竞争超时返回 false
     */
    @Override
    public boolean tryLockWithWatchdog(String key, Duration waitTime) {
        RLock lock = lock(key);
        long startNanos = System.nanoTime();
        try {
            boolean acquired = lock.tryLock(waitMillis(waitTime), TimeUnit.MILLISECONDS);
            recordAcquire(acquired, startNanos);
            return acquired;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordAcquireError(startNanos);
            throw new DistributedLockInterruptedException("Interrupted while acquiring distributed lock", exception);
        } catch (RuntimeException exception) {
            recordAcquireError(startNanos);
            throw exception;
        }
    }

    /**
     * 获取固定租约锁，竞争超时时抛出业务可识别的锁繁忙异常。
     *
     * @param key 完整 Redis 锁 Key
     * @param waitTime 最大等待时间
     * @param leaseTime 固定锁租约
     */
    @Override
    public void lock(String key, Duration waitTime, Duration leaseTime) {
        if (!tryLock(key, waitTime, leaseTime)) {
            throw new DistributedLockBusyException("Distributed lock is busy");
        }
    }

    /**
     * 仅由当前持锁线程释放可重入锁；非持有线程调用保持幂等。
     *
     * @param key 完整 Redis 锁 Key
     */
    @Override
    public void unlock(String key) {
        RLock lock = lock(key);
        if (!lock.isHeldByCurrentThread()) {
            return;
        }
        long startNanos = System.nanoTime();
        try {
            lock.unlock();
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.LOCK,
                    RedisBusinessMetrics.Operation.RELEASE,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    System.nanoTime() - startNanos
            );
        } catch (RuntimeException exception) {
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.LOCK,
                    RedisBusinessMetrics.Operation.RELEASE,
                    RedisBusinessMetrics.Outcome.ERROR,
                    System.nanoTime() - startNanos
            );
            throw exception;
        }
    }

    /**
     * 检查当前线程是否持有指定锁，用于安全释放和测试锁所有权。
     *
     * @param key 完整 Redis 锁 Key
     * @return 当前线程持锁时返回 true
     */
    @Override
    public boolean isHeldByCurrentThread(String key) {
        return lock(key).isHeldByCurrentThread();
    }

    @Override
    public <T> DistributedLockExecution<T> execute(String key,
                                                   Duration waitTime,
                                                   Duration leaseTime,
                                                   Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        boolean acquired = tryLock(key, waitTime, leaseTime);
        if (!acquired) {
            return DistributedLockExecution.contended();
        }
        try {
            return DistributedLockExecution.acquired(action.get());
        } finally {
            unlock(key);
        }
    }

    /**
     * 校验锁 Key 并获取 Redisson 可重入锁句柄，不执行远程加锁。
     *
     * @param key 完整 Redis 锁 Key
     * @return 对应 Redisson 锁句柄
     */
    private RLock lock(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Distributed lock key must not be blank");
        }
        return redissonClient.getLock(key);
    }

    /**
     * 校验锁等待时间并转换为毫秒；零表示仅尝试一次。
     *
     * @param waitTime 最大等待时间
     * @return 非负毫秒值
     */
    private long waitMillis(Duration waitTime) {
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("waitTime must not be negative");
        }
        return waitTime.toMillis();
    }

    /**
     * 校验固定租约等时长为可用的正毫秒值。
     *
     * @param duration 待校验时长
     * @param label 异常信息中的参数名称
     * @return 正毫秒值
     */
    private long positiveMillis(Duration duration, String label) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.toMillis() <= 0L) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return duration.toMillis();
    }

    /**
     * 记录锁获取成功或竞争结果及本地观测耗时。
     *
     * @param acquired 是否获得锁
     * @param startNanos 获取尝试开始时间
     */
    private void recordAcquire(boolean acquired, long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.LOCK,
                RedisBusinessMetrics.Operation.ACQUIRE,
                acquired ? RedisBusinessMetrics.Outcome.SUCCESS : RedisBusinessMetrics.Outcome.CONTENDED,
                System.nanoTime() - startNanos
        );
    }

    /**
     * 记录锁获取调用异常及本地观测耗时，不写入锁 Key。
     *
     * @param startNanos 获取尝试开始时间
     */
    private void recordAcquireError(long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.LOCK,
                RedisBusinessMetrics.Operation.ACQUIRE,
                RedisBusinessMetrics.Outcome.ERROR,
                System.nanoTime() - startNanos
        );
    }
}
