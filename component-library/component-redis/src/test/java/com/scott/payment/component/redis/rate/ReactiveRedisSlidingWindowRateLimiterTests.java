package com.scott.payment.component.redis.rate;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证响应式滑动窗口在 Redis 故障时不会绕过配置的故障策略。 */
class ReactiveRedisSlidingWindowRateLimiterTests {

    @Test
    void shouldApplyLocalFallbackAndFailClosedPolicies() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(eq(PaymentRedisScripts.slidingWindowRateLimitV1()), anyList(), anyList()))
                .thenReturn(Flux.error(new IllegalStateException("Redis unavailable")));
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");

        ReactiveRedisSlidingWindowRateLimiter local = new ReactiveRedisSlidingWindowRateLimiter(
                redis, properties, 10, RateLimitFailurePolicy.LOCAL_FALLBACK);
        ReactiveRedisSlidingWindowRateLimiter closed = new ReactiveRedisSlidingWindowRateLimiter(
                redis, properties, 10, RateLimitFailurePolicy.FAIL_CLOSED);

        assertThat(local.tryAcquire("ip", "198.51.100.7", 1, Duration.ofMinutes(1)).block().allowed())
                .isTrue();
        SlidingWindowRateLimitResult rejected =
                closed.tryAcquire("ip", "198.51.100.7", 200, Duration.ofMinutes(1)).block();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(60L);
    }
}
