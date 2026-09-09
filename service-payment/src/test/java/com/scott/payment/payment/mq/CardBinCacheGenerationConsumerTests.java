package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.CacheGenerationChangedMessage;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinCacheGenerationConsumerTests
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 验证 Card BIN generation 消费者的重复投递、门禁过期恢复和失败重试契约。
 * @status : create
 */
class CardBinCacheGenerationConsumerTests {

    @Test
    void shouldTreatDuplicateGenerationCommitAsSuccessful() {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RedisCachePublication original = originalPublication();
        when(generationStore.commit(original)).thenReturn(true);
        CardBinCacheGenerationConsumer consumer = new CardBinCacheGenerationConsumer(generationStore);
        String payload = payload();

        consumer.onMessage(payload);
        consumer.onMessage(payload);

        verify(generationStore, times(2)).commit(original);
        verify(generationStore, never()).begin(any(), any());
    }

    @Test
    void shouldPublishReplacementGenerationWhenOriginalGateExpired() {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RedisCachePublication original = originalPublication();
        RedisCachePublication replacement = new RedisCachePublication(
                "card-bin-range", "t-recovery", "g-recovery");
        when(generationStore.commit(original)).thenReturn(false);
        when(generationStore.begin("card-bin-range", Duration.ofMinutes(30))).thenReturn(replacement);
        when(generationStore.commit(replacement)).thenReturn(true);

        new CardBinCacheGenerationConsumer(generationStore).onMessage(payload());

        verify(generationStore).commit(original);
        verify(generationStore).commit(replacement);
        verify(generationStore, never()).abort(replacement);
    }

    @Test
    void shouldAbortReplacementAndThrowWhenRecoveryCommitFails() {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RedisCachePublication original = originalPublication();
        RedisCachePublication replacement = new RedisCachePublication(
                "card-bin-range", "t-recovery", "g-recovery");
        when(generationStore.commit(original)).thenReturn(false);
        when(generationStore.begin("card-bin-range", Duration.ofMinutes(30))).thenReturn(replacement);
        when(generationStore.commit(replacement)).thenReturn(false);

        assertThatThrownBy(() -> new CardBinCacheGenerationConsumer(generationStore).onMessage(payload()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recovery generation commit failed");
        verify(generationStore).abort(replacement);
    }

    private RedisCachePublication originalPublication() {
        return new RedisCachePublication("card-bin-range", "t-original", "g-original");
    }

    private String payload() {
        CacheGenerationChangedMessage message = new CacheGenerationChangedMessage();
        message.setMessageId("card-bin-cache-g-original");
        message.setNamespace("card-bin-range");
        message.setPublicationToken("t-original");
        message.setGeneration("g-original");
        message.setEventType(MqTag.CARD_BIN_CACHE_CHANGED);
        return JsonUtils.toJsonString(message);
    }
}
