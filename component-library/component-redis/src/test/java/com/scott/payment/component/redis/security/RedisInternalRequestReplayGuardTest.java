package com.scott.payment.component.redis.security;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisInternalRequestReplayGuardTest
 * @date : 2026-08-20 23:58
 * @email : scott_x@163.com
 * @description : 验证内部 HMAC nonce 使用环境隔离摘要 Key 原子占用，重复请求与 Redis 异常均失败关闭
 * @status : create
 */
class RedisInternalRequestReplayGuardTest {

    private static final String CALLER = "service-openapi";
    private static final String NONCE = "nonce-value-1234567890";
    private static final Duration TTL = Duration.ofMinutes(10);

    /** 验证首次占用成功且物理 Key 不暴露原始 nonce。 */
    @Test
    void shouldAcquireHashedNonceKey() {
        Fixture fixture = fixture(Boolean.TRUE);

        assertThat(fixture.guard().tryAcquire(CALLER, NONCE, TTL)).isTrue();
        String expectedKey = "acquiring:test:security:internal-nonce:" + CALLER + ':'
                + RedisKeyDigest.sha256(NONCE);
        verify(fixture.valueOperations()).setIfAbsent(expectedKey, "1", TTL);
        assertThat(expectedKey).doesNotContain(NONCE);
    }

    /** 验证 Redis SET NX 返回 false 时识别为重放请求。 */
    @Test
    void shouldRejectRepeatedNonce() {
        Fixture fixture = fixture(Boolean.FALSE);

        assertThat(fixture.guard().tryAcquire(CALLER, NONCE, TTL)).isFalse();
    }

    /** 验证 Redis 未返回执行结果时不允许内部请求降级放行。 */
    @Test
    void shouldFailClosedWhenRedisReturnsNull() {
        Fixture fixture = fixture(null);

        assertThatThrownBy(() -> fixture.guard().tryAcquire(CALLER, NONCE, TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned no result");
    }

    /** 验证非法 TTL 不会发起 Redis 写入。 */
    @Test
    void shouldRejectNonPositiveTtl() {
        Fixture fixture = fixture(Boolean.TRUE);

        assertThatThrownBy(() -> fixture.guard().tryAcquire(CALLER, NONCE, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl must be positive");
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(Boolean redisResult) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("1"), org.mockito.ArgumentMatchers.eq(TTL)))
                .thenReturn(redisResult);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return new Fixture(new RedisInternalRequestReplayGuard(redisTemplate, properties), valueOperations);
    }

    private record Fixture(RedisInternalRequestReplayGuard guard,
                           ValueOperations<String, String> valueOperations) {
    }
}
