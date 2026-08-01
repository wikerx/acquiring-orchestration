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
 * 基于单个 Spring 管理的 RedissonClient 提供有界、可重入的分布式锁能力。
 */
public class RedissonDistributedLockService implements DistributedLockService {

    private final RedissonClient redissonClient;
    private final RedisBusinessMetrics metrics;

    public RedissonDistributedLockService(RedissonClient redissonClient) {
        this(redissonClient, RedisBusinessMetrics.noop());
    }

    public RedissonDistributedLockService(RedissonClient redissonClient,
                                          RedisBusinessMetrics metrics) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient");
        this.metrics = metrics == null ? RedisBusinessMetrics.noop() : metrics;
    }

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

    @Override
    public void lock(String key, Duration waitTime, Duration leaseTime) {
        if (!tryLock(key, waitTime, leaseTime)) {
            throw new DistributedLockBusyException("Distributed lock is busy");
        }
    }

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

    private RLock lock(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Distributed lock key must not be blank");
        }
        return redissonClient.getLock(key);
    }

    private long waitMillis(Duration waitTime) {
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("waitTime must not be negative");
        }
        return waitTime.toMillis();
    }

    private long positiveMillis(Duration duration, String label) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.toMillis() <= 0L) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return duration.toMillis();
    }

    private void recordAcquire(boolean acquired, long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.LOCK,
                RedisBusinessMetrics.Operation.ACQUIRE,
                acquired ? RedisBusinessMetrics.Outcome.SUCCESS : RedisBusinessMetrics.Outcome.CONTENDED,
                System.nanoTime() - startNanos
        );
    }

    private void recordAcquireError(long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.LOCK,
                RedisBusinessMetrics.Operation.ACQUIRE,
                RedisBusinessMetrics.Outcome.ERROR,
                System.nanoTime() - startNanos
        );
    }
}
