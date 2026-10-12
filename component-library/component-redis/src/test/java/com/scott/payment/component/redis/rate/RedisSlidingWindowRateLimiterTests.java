package com.scott.payment.component.redis.rate;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证同步滑动窗口在 Redis 故障时执行明确配置的降级策略。 */
class RedisSlidingWindowRateLimiterTests {

    @Test
    void shouldUseBoundedLocalWindowWhenConfigured() {
        StringRedisTemplate redis = failedRedis();
        RedisSlidingWindowRateLimiter limiter = limiter(redis, RateLimitFailurePolicy.LOCAL_FALLBACK);

        assertThat(limiter.tryAcquire("merchant", "200045", 1, Duration.ofMinutes(1)).allowed())
                .isTrue();
        assertThat(limiter.tryAcquire("merchant", "200045", 1, Duration.ofMinutes(1)).allowed())
                .isFalse();
    }

    @Test
    void shouldRejectImmediatelyWhenFailClosedIsConfigured() {
        RedisSlidingWindowRateLimiter limiter = limiter(failedRedis(), RateLimitFailurePolicy.FAIL_CLOSED);

        SlidingWindowRateLimitResult result =
                limiter.tryAcquire("merchant", "200045", 200, Duration.ofMinutes(1));

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(60L);
    }

    private StringRedisTemplate failedRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(eq(PaymentRedisScripts.slidingWindowRateLimitV1()), anyList(),
                anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        return redis;
    }

    private RedisSlidingWindowRateLimiter limiter(StringRedisTemplate redis,
                                                   RateLimitFailurePolicy failurePolicy) {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return new RedisSlidingWindowRateLimiter(redis, properties, 10, failurePolicy);
    }
}
