package com.scott.payment.component.redis.generation;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheGenerationStoreTests
 * @date : 2026-07-30 11:10
 * @email : scott_x@163.com
 * @description : 验证缓存代际存储对正常读取和发布门禁的公共行为契约
 * @status : create
 */
class RedisCacheGenerationStoreTests {

    @Test
    void shouldExposeActiveGenerationWhenNoPublicationIsPending() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.same(PaymentRedisScripts.cacheGenerationReadV1()),
                anyList(),
                anyString()
        )).thenReturn("ACTIVE:g-20260730");
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties()
        );

        RedisCacheGenerationState state = store.current("risk-runtime-rule");

        assertThat(state.cacheReadable()).isTrue();
        assertThat(state.generation()).isEqualTo("g-20260730");
        String slotTag = RedisKeyDigest.sha256("risk-runtime-rule");
        verify(redisTemplate).execute(
                same(PaymentRedisScripts.cacheGenerationReadV1()),
                eq(java.util.List.of(
                        "acquiring:test:cache:generation:{" + slotTag + "}:current",
                        "acquiring:test:cache:generation:{" + slotTag + "}:publication"
                )),
                anyString()
        );
    }

    @Test
    void shouldMarkCacheUnreadableWhilePublicationIsPending() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.same(PaymentRedisScripts.cacheGenerationReadV1()),
                anyList(),
                anyString()
        )).thenReturn("PENDING");
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties()
        );

        RedisCacheGenerationState state = store.current("risk-runtime-rule");

        assertThat(state.cacheReadable()).isFalse();
        assertThat(state.generation()).isNull();
    }

    @Test
    void shouldBeginSinglePublicationAndRejectConcurrentPublisher() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.same(PaymentRedisScripts.cacheGenerationBeginV1()),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(1L, 0L);
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties()
        );

        RedisCachePublication publication = store.begin(
                "risk-runtime-rule",
                Duration.ofMinutes(10)
        );

        assertThat(publication.namespace()).isEqualTo("risk-runtime-rule");
        assertThat(publication.token()).isNotBlank();
        assertThat(publication.generation()).startsWith("g-");
        assertThatThrownBy(() -> store.begin("risk-runtime-rule", Duration.ofMinutes(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in progress");
    }

    @Test
    void shouldCommitGenerationOnlyForPublicationOwnerOrIdempotentRetry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.same(PaymentRedisScripts.cacheGenerationCommitV1()),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(1L, 1L, 0L);
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties()
        );
        RedisCachePublication publication = new RedisCachePublication(
                "risk-runtime-rule",
                "t-owner",
                "g-next"
        );

        assertThat(store.commit(publication)).isTrue();
        assertThat(store.commit(publication)).isTrue();
        assertThat(store.commit(new RedisCachePublication(
                "risk-runtime-rule",
                "t-foreign",
                "g-foreign"
        ))).isFalse();
    }

    @Test
    void shouldAbortPublicationOnlyForGateOwner() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.same(PaymentRedisScripts.tokenLeaseReleaseV1()),
                anyList(),
                anyString()
        )).thenReturn(1L, 0L);
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties()
        );

        assertThat(store.abort(new RedisCachePublication(
                "risk-runtime-rule",
                "t-owner",
                "g-next"
        ))).isTrue();
        assertThat(store.abort(new RedisCachePublication(
                "risk-runtime-rule",
                "t-foreign",
                "g-other"
        ))).isFalse();
    }

    private PaymentRedisProperties redisProperties() {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return properties;
    }
}
