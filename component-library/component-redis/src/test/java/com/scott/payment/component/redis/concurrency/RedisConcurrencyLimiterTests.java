package com.scott.payment.component.redis.concurrency;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 集群级并发租约测试。
 */
class RedisConcurrencyLimiterTests {

    @Test
    void shouldExecuteAndReleaseLeaseWithoutExposingIdentityInKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        RedisConcurrencyLimiter limiter = new RedisConcurrencyLimiter(redisTemplate, properties);
        boolean[] executed = {false};

        boolean acquired = limiter.execute(
                "transaction", "admin-export", "sensitive-account", 1,
                Duration.ofMinutes(5), () -> executed[0] = true);

        assertThat(acquired).isTrue();
        assertThat(executed[0]).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(), keys.capture(), eq("300000"), eq("1"), anyString());
        assertThat(keys.getValue().get(0)).startsWith("acquiring:test:transaction:admin-export:{");
        assertThat(keys.getValue().get(0)).doesNotContain("sensitive-account");
        verify(zSetOperations).remove(eq(keys.getValue().get(0)), anyString());
    }

    @Test
    void shouldRejectWhenConcurrencyBudgetIsFull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(0L);
        RedisConcurrencyLimiter limiter = new RedisConcurrencyLimiter(
                redisTemplate, new PaymentRedisProperties());
        boolean[] executed = {false};

        boolean acquired = limiter.execute(
                "transaction", "merchant-export", "merchant-account", 1,
                Duration.ofMinutes(5), () -> executed[0] = true);

        assertThat(acquired).isFalse();
        assertThat(executed[0]).isFalse();
        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    void shouldPreserveActionFailureWhenLeaseReleaseFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(zSetOperations).remove(anyString(), anyString());
        RedisConcurrencyLimiter limiter = new RedisConcurrencyLimiter(
                redisTemplate, new PaymentRedisProperties());

        assertThatThrownBy(() -> limiter.execute(
                "transaction", "admin-export", "account", 1,
                Duration.ofMinutes(5), () -> {
                    throw new IllegalArgumentException("export failed");
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("export failed");
    }

    @Test
    void shouldRenewLeaseWhileLongRunningActionIsActive() throws InterruptedException {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        when(redisTemplate.execute(any(), any(List.class), anyString(), anyString()))
                .thenReturn(1L);
        RedisConcurrencyLimiter limiter = new RedisConcurrencyLimiter(
                redisTemplate, new PaymentRedisProperties());

        boolean acquired = limiter.execute(
                "transaction", "admin-export", "account", 1,
                Duration.ofMillis(90), () -> {
                    try {
                        Thread.sleep(80L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("test interrupted", exception);
                    }
                });

        assertThat(acquired).isTrue();
        verify(redisTemplate, timeout(500).atLeastOnce())
                .execute(eq(PaymentRedisScripts.concurrencyLeaseRenewV1()),
                        any(List.class), eq("90"), anyString());
    }
}
