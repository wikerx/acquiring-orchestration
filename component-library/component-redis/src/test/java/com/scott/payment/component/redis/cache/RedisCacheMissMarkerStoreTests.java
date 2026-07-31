package com.scott.payment.component.redis.cache;

import com.scott.payment.component.core.cache.CacheMissMarkerStore;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheMissMarkerStoreTests
 * @date : 2026-07-30 21:40
 * @email : scott_x@163.com
 * @description : Redis 空结果标记三态、精简 Key 与短 TTL 抖动策略单元测试
 * @status : create
 */
@Slf4j
class RedisCacheMissMarkerStoreTests {

    /**
     * Redis 返回 marker 或空值时必须分别映射为 PRESENT 和 ABSENT。
     */
    @Test
    void shouldDistinguishPresentAndAbsentMarkers() {
        log.info("测试 miss marker 正常三态映射，关键输入: 相同商户 Key 依次返回 marker 和 null");
        Fixture fixture = fixture();
        when(fixture.valueOperations().get("acquiring:dev:merchant:runtime-profile-miss:200045"))
                .thenReturn("1", (String) null);

        CacheMissMarkerStore.LookupStatus present = fixture.store().lookup(
                "merchant", "runtime-profile-miss", "200045");
        CacheMissMarkerStore.LookupStatus absent = fixture.store().lookup(
                "merchant", "runtime-profile-miss", "200045");

        assertThat(present).isEqualTo(CacheMissMarkerStore.LookupStatus.PRESENT);
        assertThat(absent).isEqualTo(CacheMissMarkerStore.LookupStatus.ABSENT);
        log.info("miss marker 正常三态映射验证完成，结果: PRESENT 后为 ABSENT");
    }

    /**
     * Redis 读取异常必须返回 UNAVAILABLE，不能伪装成业务不存在。
     */
    @Test
    void shouldReportUnavailableWhenRedisReadFails() {
        log.info("测试 miss marker Redis 故障语义，关键输入: GET 抛出连接异常");
        Fixture fixture = fixture();
        when(fixture.valueOperations().get(anyString()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        CacheMissMarkerStore.LookupStatus result = fixture.store().lookup(
                "merchant", "runtime-profile-miss", "200045");

        assertThat(result).isEqualTo(CacheMissMarkerStore.LookupStatus.UNAVAILABLE);
        log.info("miss marker Redis 故障语义验证完成，结果: UNAVAILABLE");
    }

    /**
     * marker 写入必须使用精简业务 Key，TTL 保持在基础值正负抖动范围内。
     */
    @Test
    void shouldWriteMarkerWithConciseKeyAndBoundedTtl() {
        log.info("测试 miss marker 写入，关键输入: 30 秒基础 TTL、10% 抖动");
        Fixture fixture = fixture();
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        fixture.store().markMissing(
                "merchant",
                "runtime-profile-miss",
                "200045",
                Duration.ofSeconds(30),
                10
        );

        verify(fixture.valueOperations()).set(
                org.mockito.ArgumentMatchers.eq(
                        "acquiring:dev:merchant:runtime-profile-miss:200045"),
                org.mockito.ArgumentMatchers.eq("1"),
                ttlCaptor.capture()
        );
        assertThat(ttlCaptor.getValue())
                .isBetween(Duration.ofSeconds(27), Duration.ofSeconds(33));
        log.info("miss marker 写入验证完成，结果 TTL 毫秒数: {}", ttlCaptor.getValue().toMillis());
    }

    /**
     * marker 删除异常必须向可靠失效调用方传播。
     */
    @Test
    void shouldPropagateEvictionFailureForOutboxRetry() {
        log.info("测试 miss marker 可靠失效，关键输入: Redis DELETE 抛出异常");
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("redis unavailable"))
                .when(fixture.stringRedisTemplate())
                .delete("acquiring:dev:merchant:runtime-profile-miss:200045");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.store().evict(
                        "merchant", "runtime-profile-miss", "200045"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redis unavailable");
        log.info("miss marker 可靠失效验证完成，结果: 删除异常已向上游传播");
    }

    /**
     * 创建使用固定 dev 环境 Key 的测试夹具。
     *
     * @return Redis 模板、ValueOperations 与待测存储
     */
    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        PaymentRedisKeyResolver keyResolver = mock(PaymentRedisKeyResolver.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(keyResolver.businessKey(
                "merchant", "runtime-profile-miss", "200045"))
                .thenReturn("acquiring:dev:merchant:runtime-profile-miss:200045");
        return new Fixture(
                redisTemplate,
                valueOperations,
                new RedisCacheMissMarkerStore(redisTemplate, keyResolver)
        );
    }

    /**
     * Redis miss marker 测试依赖集合。
     *
     * @param stringRedisTemplate Redis 字符串模板
     * @param valueOperations     Redis String 操作接口
     * @param store               待测 marker 存储
     */
    private record Fixture(StringRedisTemplate stringRedisTemplate,
                           ValueOperations<String, String> valueOperations,
                           RedisCacheMissMarkerStore store) {
    }
}
