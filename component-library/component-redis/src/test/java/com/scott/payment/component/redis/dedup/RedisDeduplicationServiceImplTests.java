package com.scott.payment.component.redis.dedup;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.dedup.impl.RedisDeduplicationServiceImpl;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis Set 去重原子写入与返回语义测试。 */
class RedisDeduplicationServiceImplTests {

    /** 首次写入应原子设置 TTL，并继续返回非重复。 */
    @Test
    void shouldReturnNotDuplicateWhenScriptAddsMember() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                same(PaymentRedisScripts.setDedupAddV1()),
                org.mockito.ArgumentMatchers.eq(List.of("dedup:key")),
                org.mockito.ArgumentMatchers.eq("value-1"),
                org.mockito.ArgumentMatchers.eq("30000")))
                .thenReturn(1L);
        RedisDeduplicationService service = service(redisTemplate);

        assertThat(service.checkAndAdd("dedup:key", "value-1", Duration.ofSeconds(30))).isFalse();

        verify(redisTemplate).execute(
                same(PaymentRedisScripts.setDedupAddV1()),
                eq(List.of("dedup:key")),
                eq("value-1"),
                eq("30000"));
        verify(redisTemplate, never()).opsForSet();
    }

    /** 已存在成员应继续返回重复，且由脚本决定不刷新 TTL。 */
    @Test
    void shouldReturnDuplicateWhenScriptFindsExistingMember() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                same(PaymentRedisScripts.setDedupAddV1()),
                org.mockito.ArgumentMatchers.eq(List.of("dedup:key")),
                org.mockito.ArgumentMatchers.eq("value-1"),
                org.mockito.ArgumentMatchers.eq("30000")))
                .thenReturn(0L);
        RedisDeduplicationService service = service(redisTemplate);

        assertThat(service.checkAndAdd("dedup:key", "value-1", Duration.ofSeconds(30))).isTrue();
    }

    /** 未配置有效 TTL 时仍应完成去重写入，但向脚本传入零表示不设置过期。 */
    @Test
    void shouldPreserveNonExpiringSetBehaviorWhenTtlIsMissing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                same(PaymentRedisScripts.setDedupAddV1()),
                org.mockito.ArgumentMatchers.eq(List.of("dedup:key")),
                org.mockito.ArgumentMatchers.eq("value-1"),
                org.mockito.ArgumentMatchers.eq("0")))
                .thenReturn(1L);
        RedisDeduplicationService service = service(redisTemplate);

        assertThat(service.checkAndAdd("dedup:key", "value-1", null)).isFalse();
    }

    /** Redis 未返回结果时保持旧的保守语义，按重复处理。 */
    @Test
    void shouldTreatMissingRedisResultAsDuplicate() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisDeduplicationService service = service(redisTemplate);

        assertThat(service.checkAndAdd("dedup:key", "value-1", Duration.ofSeconds(30))).isTrue();
    }

    private RedisDeduplicationService service(StringRedisTemplate redisTemplate) {
        return new RedisDeduplicationServiceImpl(redisTemplate, new PaymentRedisProperties());
    }
}
