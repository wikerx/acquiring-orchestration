package com.scott.payment.component.redis.lock;

import com.scott.payment.component.redis.lock.impl.RedissonDistributedLockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedissonDistributedLockServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Redisson 分布式锁的有界获取、中断处理和持有者安全释放行为。
 * @status : create
 */
class RedissonDistributedLockServiceTests {

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @Test
    void shouldFailFastWhenLockIsContended() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lock-key")).thenReturn(lock);
        when(lock.tryLock(0L, 30_000L, TimeUnit.MILLISECONDS)).thenReturn(false);

        DistributedLockService lockService = new RedissonDistributedLockService(redissonClient);

        assertThat(lockService.tryLock("lock-key", Duration.ZERO, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void shouldOnlyUnlockWhenCurrentThreadOwnsLock() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lock-key")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(false, true);
        DistributedLockService lockService = new RedissonDistributedLockService(redissonClient);

        lockService.unlock("lock-key");
        verify(lock, never()).unlock();

        lockService.unlock("lock-key");
        verify(lock).unlock();
    }

    @Test
    void shouldRestoreInterruptedStatusWhenAcquireIsInterrupted() throws InterruptedException {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock("lock-key")).thenReturn(lock);
        when(lock.tryLock(0L, 30_000L, TimeUnit.MILLISECONDS))
                .thenThrow(new InterruptedException("interrupted"));
        DistributedLockService lockService = new RedissonDistributedLockService(redissonClient);

        assertThatThrownBy(() -> lockService.tryLock(
                "lock-key", Duration.ZERO, Duration.ofSeconds(30)))
                .isInstanceOf(DistributedLockInterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
