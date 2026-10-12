package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证支付与 OpenAPI 共用的 Card BIN 启动初始化门禁。 */
class CardBinRangeIndexInitializerTests {

    @Test
    void shouldSkipReplicaScanWhenCurrentIndexIsComplete() {
        CardBinRangeIndex rangeIndex = mock(CardBinRangeIndex.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(rangeIndex.hasActiveSnapshot()).thenReturn(true);

        new CardBinRangeIndexInitializer(rangeIndex, generationStore)
                .run(mock(ApplicationArguments.class));

        verify(generationStore, never()).begin(anyString(), any(Duration.class));
        verify(rangeIndex, never()).publishFromReplica(anyString());
    }

    @Test
    void shouldPublishAndCommitWhenIndexIsMissingOrOutdated() {
        CardBinRangeIndex rangeIndex = mock(CardBinRangeIndex.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RedisCachePublication publication = new RedisCachePublication(
                "card-bin-range", "t-owner", "g-next");
        when(rangeIndex.hasActiveSnapshot()).thenReturn(false);
        when(generationStore.begin(eq("card-bin-range"), any(Duration.class)))
                .thenReturn(publication);
        when(rangeIndex.publishFromReplica("g-next")).thenReturn("g-next");
        when(generationStore.commit(publication)).thenReturn(true);

        new CardBinRangeIndexInitializer(rangeIndex, generationStore)
                .run(mock(ApplicationArguments.class));

        verify(rangeIndex).publishFromReplica("g-next");
        verify(generationStore).commit(publication);
        verify(generationStore, never()).abort(publication);
    }
}
