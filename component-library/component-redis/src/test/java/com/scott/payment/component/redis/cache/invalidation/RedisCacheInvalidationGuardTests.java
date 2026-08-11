package com.scott.payment.component.redis.cache.invalidation;

import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 受管永久缓存失效 Redis 门禁测试。
 */
@Slf4j
class RedisCacheInvalidationGuardTests {

    /**
     * 验证商户资料门禁的获取、读取和 token 比较释放使用同一个短 Key。
     */
    @Test
    void shouldAcquireCheckAndReleaseConciseMerchantGate() {
        log.info("测试商户资料永久缓存门禁，关键输入: cacheName=merchant:info, merchantId=200045");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofHours(2))))
                .thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(redisTemplate.execute(any(), any(), anyString())).thenReturn(1L);
        RedisCacheInvalidationGuard guard = new RedisCacheInvalidationGuard(
                redisTemplate,
                redisProperties()
        );

        CacheInvalidationLease lease = guard.acquire(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045",
                Duration.ofHours(2)
        );

        assertThat(lease.token()).startsWith("t-");
        assertThat(guard.isPending(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045"
        )).isTrue();
        assertThat(guard.release(lease)).isTrue();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(
                keyCaptor.capture(),
                eq(lease.token()),
                eq(Duration.ofHours(2))
        );
        assertThat(keyCaptor.getValue())
                .isEqualTo("acquiring:test:merchant:info:pending:200045");
        log.info("商户资料永久缓存门禁测试完成，结果: 获取、查询和释放均使用短 Key");
    }

    /**
     * 验证 OpenAPI 策略和系统参数使用相互隔离的 pending Key。
     */
    @Test
    void shouldBuildConciseOpenApiAndSystemConfigGateKeys() {
        log.info("测试永久缓存短门禁 Key，关键输入: merchant:openapi 与 system:config");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofHours(2))))
                .thenReturn(true);
        RedisCacheInvalidationGuard guard = new RedisCacheInvalidationGuard(
                redisTemplate,
                redisProperties()
        );

        guard.acquire(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045",
                Duration.ofHours(2)
        );
        guard.acquire(
                PaymentCacheNames.SYSTEM_CONFIG,
                "platform.gateway.base-url",
                Duration.ofHours(2)
        );

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, org.mockito.Mockito.times(2)).setIfAbsent(
                keyCaptor.capture(),
                anyString(),
                eq(Duration.ofHours(2))
        );
        assertThat(keyCaptor.getAllValues()).isEqualTo(List.of(
                "acquiring:test:merchant:openapi:pending:200045",
                "acquiring:test:system:configPending:"
                        + com.scott.payment.component.redis.support.RedisKeyDigest
                        .sha256("platform.gateway.base-url")
        ));
        log.info("永久缓存短门禁 Key 测试完成，结果: 两个命名空间均符合 acquiring:test 短格式");
    }

    /**
     * 验证后台用户资料使用独立的 admin 命名空间门禁 Key。
     */
    @Test
    void shouldBuildAdminUserProfileGateKey() {
        log.info("测试后台用户资料失效门禁，关键输入: accountId=10001");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofHours(2))))
                .thenReturn(true);
        RedisCacheInvalidationGuard guard = new RedisCacheInvalidationGuard(
                redisTemplate,
                redisProperties()
        );

        guard.acquire(
                PaymentCacheNames.ADMIN_USER_PROFILE,
                "10001",
                Duration.ofHours(2)
        );

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(
                keyCaptor.capture(),
                anyString(),
                eq(Duration.ofHours(2))
        );
        assertThat(keyCaptor.getValue())
                .isEqualTo("acquiring:test:admin:user:profile:pending:10001");
        log.info("后台用户资料失效门禁验证完成，结果: 使用独立 admin 命名空间");
    }

    /**
     * 验证并发持有者和未登记缓存名称均被门禁组件拒绝。
     */
    @Test
    void shouldRejectConcurrentOrUnregisteredInvalidation() {
        log.info("测试永久缓存门禁拒绝规则，关键输入: 并发获取与未登记 cacheName");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);
        RedisCacheInvalidationGuard guard = new RedisCacheInvalidationGuard(
                redisTemplate,
                redisProperties()
        );

        assertThatIllegalStateException().isThrownBy(() -> guard.acquire(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045",
                Duration.ofMinutes(30)
        )).withMessageContaining("already in progress");
        assertThatIllegalArgumentException().isThrownBy(() -> guard.isPending(
                "config:private",
                "system.name"
        )).withMessageContaining("does not allow");
        log.info("永久缓存门禁拒绝规则测试完成，结果: 并发获取和未登记缓存均被拒绝");
    }

    /**
     * 验证 Redis 无法返回确定 pending 状态时不会错误放行缓存读取。
     */
    @Test
    void shouldTreatUnknownRedisPendingStateAsFailure() {
        log.info("测试永久缓存门禁未知状态，关键输入: Redis hasKey 返回 null");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(null);
        RedisCacheInvalidationGuard guard = new RedisCacheInvalidationGuard(
                redisTemplate,
                redisProperties()
        );

        assertThatIllegalStateException().isThrownBy(() -> guard.isPending(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        )).withMessageContaining("unknown");
        log.info("永久缓存门禁未知状态测试完成，结果: 抛出异常交由读取端降级主库");
    }

    /**
     * 创建使用 test 环境前缀的 Redis 配置。
     *
     * @return 测试 Redis Key 配置
     */
    private PaymentRedisProperties redisProperties() {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return properties;
    }
}
