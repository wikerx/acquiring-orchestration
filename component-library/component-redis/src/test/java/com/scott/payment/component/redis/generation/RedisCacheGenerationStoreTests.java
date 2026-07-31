package com.scott.payment.component.redis.generation;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
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
@Slf4j
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
                org.mockito.ArgumentMatchers.same(PaymentRedisScripts.lockReleaseV1()),
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

    /**
     * 双写历史优先阶段必须先读取历史代际，并用相同 generation 初始化精简 Key。
     */
    @Test
    void shouldInitializeCompactGenerationFromLegacyDuringDualMigration() {
        log.info("测试双写历史优先代际读取，关键输入: legacy generation=g-20260730");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                same(PaymentRedisScripts.cacheGenerationReadV1()),
                anyList(),
                anyString()
        )).thenReturn("ACTIVE:g-20260730", "ACTIVE:g-20260730");
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties(PaymentRedisProperties.KeyMigrationMode.DUAL_LEGACY_FIRST)
        );

        RedisCacheGenerationState state = store.current("risk-runtime-rule");

        assertThat(state).isEqualTo(RedisCacheGenerationState.active("g-20260730"));
        verify(redisTemplate).execute(
                same(PaymentRedisScripts.cacheGenerationReadV1()),
                eq(java.util.List.of(
                        "acquiring:test:component-redis:cache:generation:v1:risk-runtime-rule:current",
                        "acquiring:test:component-redis:cache:generation:v1:risk-runtime-rule:publication"
                )),
                anyString()
        );
        verify(redisTemplate).execute(
                same(PaymentRedisScripts.cacheGenerationReadV1()),
                eq(java.util.List.of(
                        "acquiring:test:cache:generation:risk-runtime-rule:current",
                        "acquiring:test:cache:generation:risk-runtime-rule:publication"
                )),
                eq("g-20260730")
        );
        log.info("双写历史优先代际读取测试完成，结果: 精简 Key 沿用历史 generation");
    }

    /**
     * 双写阶段新旧 generation 不一致时必须关闭缓存读取，避免混用两套规则快照。
     */
    @Test
    void shouldMarkCacheUnreadableWhenDualGenerationsDiffer() {
        log.info("测试双写代际差异保护，关键输入: legacy=g-old, compact=g-new");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                same(PaymentRedisScripts.cacheGenerationReadV1()),
                anyList(),
                anyString()
        )).thenReturn("ACTIVE:g-old", "ACTIVE:g-new");
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties(PaymentRedisProperties.KeyMigrationMode.DUAL_LEGACY_FIRST)
        );

        RedisCacheGenerationState state = store.current("risk-runtime-rule");

        assertThat(state.cacheReadable()).isFalse();
        assertThat(state.generation()).isNull();
        log.info("双写代际差异保护测试完成，结果: 缓存被标记为不可读");
    }

    /**
     * 第二个发布门禁竞争失败时必须释放已取得的首个门禁，避免迁移补偿留下阻塞状态。
     */
    @Test
    void shouldReleaseLegacyGateWhenCompactGateCannotBeAcquired() {
        log.info("测试双发布门禁补偿，关键输入: legacy 获取成功、compact 已被占用");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                same(PaymentRedisScripts.cacheGenerationBeginV1()),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(1L, 0L);
        when(redisTemplate.execute(
                same(PaymentRedisScripts.lockReleaseV1()),
                anyList(),
                anyString()
        )).thenReturn(1L);
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties(PaymentRedisProperties.KeyMigrationMode.DUAL_LEGACY_FIRST)
        );

        assertThatThrownBy(() -> store.begin("risk-runtime-rule", Duration.ofMinutes(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in progress");

        verify(redisTemplate).execute(
                same(PaymentRedisScripts.lockReleaseV1()),
                eq(java.util.List.of(
                        "acquiring:test:component-redis:cache:generation:v1:risk-runtime-rule:publication"
                )),
                anyString()
        );
        log.info("双发布门禁补偿测试完成，结果: 已取得的 legacy 门禁被释放");
    }

    /**
     * 精简 Key 优先阶段必须先提交历史 generation，最后切换精简 generation。
     */
    @Test
    void shouldCommitNonPreferredGenerationBeforeCompactGeneration() {
        log.info("测试双写代际提交顺序，关键输入: compact 为首选 Key 家族");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                same(PaymentRedisScripts.cacheGenerationCommitV1()),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(1L, 1L);
        RedisCacheGenerationStore store = new RedisCacheGenerationStore(
                redisTemplate,
                redisProperties(PaymentRedisProperties.KeyMigrationMode.DUAL_COMPACT_FIRST)
        );
        RedisCachePublication publication = new RedisCachePublication(
                "risk-runtime-rule", "t-owner", "g-next");

        assertThat(store.commit(publication)).isTrue();

        InOrder order = inOrder(redisTemplate);
        order.verify(redisTemplate).execute(
                same(PaymentRedisScripts.cacheGenerationCommitV1()),
                eq(java.util.List.of(
                        "acquiring:test:component-redis:cache:generation:v1:risk-runtime-rule:current",
                        "acquiring:test:component-redis:cache:generation:v1:risk-runtime-rule:publication"
                )),
                eq("t-owner"),
                eq("g-next")
        );
        order.verify(redisTemplate).execute(
                same(PaymentRedisScripts.cacheGenerationCommitV1()),
                eq(java.util.List.of(
                        "acquiring:test:cache:generation:risk-runtime-rule:current",
                        "acquiring:test:cache:generation:risk-runtime-rule:publication"
                )),
                eq("t-owner"),
                eq("g-next")
        );
        log.info("双写代际提交顺序测试完成，结果: legacy 先提交、compact 最后提交");
    }

    private PaymentRedisProperties redisProperties() {
        return redisProperties(PaymentRedisProperties.KeyMigrationMode.LEGACY_ONLY);
    }

    /**
     * 构造指定迁移模式的测试 Redis 配置。
     *
     * @param mode Redis Key 迁移模式
     * @return 使用 test 环境前缀的配置
     */
    private PaymentRedisProperties redisProperties(PaymentRedisProperties.KeyMigrationMode mode) {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        properties.setKeyMigrationMode(mode);
        return properties;
    }
}
