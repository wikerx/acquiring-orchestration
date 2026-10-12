package com.scott.payment.component.redis.rate;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** 精确验证本地降级窗口的边界、拒绝不计数和身份容量保护。 */
class LocalSlidingWindowRateLimiterTests {

    @Test
    void shouldReleaseCapacityAtExactRollingWindowBoundary() {
        AtomicLong clock = new AtomicLong(1_000L);
        LocalSlidingWindowRateLimiter limiter = new LocalSlidingWindowRateLimiter(10, clock::get);

        assertThat(limiter.tryAcquire("merchant-1", 2, Duration.ofSeconds(60)).allowed()).isTrue();
        clock.set(30_000L);
        assertThat(limiter.tryAcquire("merchant-1", 2, Duration.ofSeconds(60)).allowed()).isTrue();
        clock.set(30_001L);
        SlidingWindowRateLimitResult rejected =
                limiter.tryAcquire("merchant-1", 2, Duration.ofSeconds(60));
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(31L);

        clock.set(61_000L);
        assertThat(limiter.tryAcquire("merchant-1", 2, Duration.ofSeconds(60)).allowed()).isTrue();
    }

    @Test
    void shouldNotInsertRejectedRequestIntoWindow() {
        AtomicLong clock = new AtomicLong(0L);
        LocalSlidingWindowRateLimiter limiter = new LocalSlidingWindowRateLimiter(10, clock::get);

        assertThat(limiter.tryAcquire("ip-1", 1, Duration.ofSeconds(60)).allowed()).isTrue();
        clock.set(10_000L);
        assertThat(limiter.tryAcquire("ip-1", 1, Duration.ofSeconds(60)).allowed()).isFalse();
        clock.set(60_000L);
        assertThat(limiter.tryAcquire("ip-1", 1, Duration.ofSeconds(60)).allowed()).isTrue();
    }

    @Test
    void shouldRejectNewIdentityWhenLocalCapacityIsExhausted() {
        AtomicLong clock = new AtomicLong(1_000L);
        LocalSlidingWindowRateLimiter limiter = new LocalSlidingWindowRateLimiter(1, clock::get);

        assertThat(limiter.tryAcquire("ip-1", 2, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(limiter.tryAcquire("ip-2", 2, Duration.ofMinutes(1)).allowed()).isFalse();
    }
}
