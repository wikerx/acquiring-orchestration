package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.reference.entity.CardBinIndexSourceSignatureDO;
import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.mapper.CardBinLookupMapper;
import com.scott.payment.component.db.reference.model.CardBinRangeIndexEntry;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Card BIN 分片索引的代际隔离、最优匹配、批量发布和资源边界。 */
class CardBinRangeIndexTests {

    @Test
    void shouldChooseMostPreciseNewestAndLargestIdFromCurrentGeneration() {
        TestContext context = context();
        List<CardBinRangeIndexEntry> candidates = List.of(
                entry(1L, 6, "VISA", LocalDateTime.of(2026, 1, 1, 0, 0)),
                entry(2L, 8, "OLD", LocalDateTime.of(2026, 1, 2, 0, 0)),
                entry(3L, 8, "NEW", LocalDateTime.of(2026, 1, 3, 0, 0)),
                entry(4L, 8, "LARGEST_ID", LocalDateTime.of(2026, 1, 3, 0, 0))
        );
        when(context.generationStore().current("card-bin-range"))
                .thenReturn(RedisCacheGenerationState.active("g-current"));
        when(context.hash().multiGet(eq("acquiring:test:cardBin:512"), anyList()))
                .thenReturn(hashValues("g-current", JsonUtils.toJsonString(candidates)));

        var lookup = context.index().lookup("51234567890");

        assertThat(lookup.available()).isTrue();
        assertThat(lookup.result().matched()).isTrue();
        assertThat(lookup.result().binLength()).isEqualTo(8);
        assertThat(lookup.result().cardBrand()).isEqualTo("LARGEST_ID");
        verify(context.hash()).multiGet(eq("acquiring:test:cardBin:512"),
                eq(List.of("_generation", "3:512", "4:5123", "5:51234", "6:512345",
                        "7:5123456", "8:51234567", "9:512345678", "10:5123456789",
                        "11:51234567890")));
    }

    @Test
    void shouldFallBackWhenPublicationIsPendingOrShardGenerationDiffers() {
        TestContext context = context();
        when(context.generationStore().current("card-bin-range"))
                .thenReturn(RedisCacheGenerationState.pending(),
                        RedisCacheGenerationState.active("g-current"));

        assertThat(context.index().lookup("51234567890").available()).isFalse();
        verify(context.hash(), never()).multiGet(anyString(), anyList());

        when(context.hash().multiGet(anyString(), anyList()))
                .thenReturn(hashValues("g-stale", "[]"));

        assertThat(context.index().lookup("51234567890").available()).isFalse();
    }

    @Test
    void shouldReturnAuthoritativeMissForCompleteEmptyShard() {
        TestContext context = context();
        when(context.generationStore().current("card-bin-range"))
                .thenReturn(RedisCacheGenerationState.active("g-current"));
        when(context.hash().multiGet(anyString(), anyList()))
                .thenReturn(hashValues("g-current", null));

        var lookup = context.index().lookup("62042558512");

        assertThat(lookup.available()).isTrue();
        assertThat(lookup.result().matched()).isFalse();
    }

    @Test
    void shouldPublishStableReplicaIntoDatabaseBoundedShards() {
        TestContext context = context();
        publicationRedis(context);
        when(context.mapper().selectIndexSourceSignature())
                .thenReturn(signature(1L, 7L), signature(1L, 7L));
        when(context.mapper().selectIndexPage(0L, 10)).thenReturn(List.of(row()));

        assertThat(context.index().publishFromReplica("g-next")).isEqualTo("g-next");

        verify(context.redis()).unlink(List.of(
                "acquiring:test:cardBin:rangeIndex:{c12c2bd490593bedfb642d4f49e90cfdcdea334e1474807cf69516deb31e272b}:active",
                "acquiring:test:cardBin:rangeIndex:{c12c2bd490593bedfb642d4f49e90cfdcdea334e1474807cf69516deb31e272b}:slot:a",
                "acquiring:test:cardBin:rangeIndex:{c12c2bd490593bedfb642d4f49e90cfdcdea334e1474807cf69516deb31e272b}:slot:b",
                "acquiring:test:cardBin:rangeIndex:{c12c2bd490593bedfb642d4f49e90cfdcdea334e1474807cf69516deb31e272b}:publication-lock"
        ));
        verify(context.redis(), times(1_000)).unlink(anyString());
        verify(context.redis()).execute(eq(PaymentRedisScripts.cardBinBucketAppendV1()),
                eq(List.of("acquiring:test:cardBin:512")), any(Object[].class));
        verify(context.hash(), times(1_000)).put(anyString(), eq("_generation"), eq("g-next"));
        verify(context.values()).set("acquiring:test:cardBin:index:ready", "g-next");
    }

    @Test
    void shouldNotActivateWhenReplicaChangesDuringBuild() {
        TestContext context = context();
        publicationRedis(context);
        when(context.mapper().selectIndexSourceSignature())
                .thenReturn(signature(1L, 7L), signature(1L, 8L));
        when(context.mapper().selectIndexPage(0L, 10)).thenReturn(List.of(row()));

        assertThatThrownBy(() -> context.index().publishFromReplica("g-next"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed while building");

        verify(context.hash(), never()).put(anyString(), eq("_generation"), anyString());
        verify(context.values(), never()).set("acquiring:test:cardBin:index:ready", "g-next");
        verify(context.redis(), atLeastOnce()).execute(eq(PaymentRedisScripts.tokenLeaseReleaseV1()),
                anyList(), anyString());
    }

    @Test
    void shouldSplitWritesIntoSmallBatches() {
        TestContext context = context(2, 4096, 100_000);
        publicationRedis(context);
        when(context.redis().execute(eq(PaymentRedisScripts.cardBinBucketAppendV1()),
                anyList(), any(Object[].class))).thenReturn(2L, 1L);
        List<CardBinRangeDO> rows = List.of(
                row(1L, 51234500000L, 51234599999L, 6),
                row(2L, 51234600000L, 51234699999L, 6),
                row(3L, 51234700000L, 51234799999L, 6));
        when(context.mapper().selectIndexSourceSignature())
                .thenReturn(signature(3L, 7L), signature(3L, 7L));
        when(context.mapper().selectIndexPage(0L, 10)).thenReturn(rows);

        context.index().publishFromReplica("g-next");

        verify(context.redis(), times(2)).execute(eq(PaymentRedisScripts.cardBinBucketAppendV1()),
                eq(List.of("acquiring:test:cardBin:512")), any(Object[].class));
    }

    @Test
    void shouldAbortBeforeActivationWhenSnapshotByteBudgetIsExceeded() {
        TestContext context = context(100, 4096, 4096);
        publicationRedis(context);
        List<CardBinRangeDO> rows = List.of(
                row(1L, 51234500000L, 51234599999L, 6),
                row(2L, 51234600000L, 51234699999L, 6),
                row(3L, 51234700000L, 51234799999L, 6),
                row(4L, 51234800000L, 51234899999L, 6),
                row(5L, 51234900000L, 51234999999L, 6));
        rows.forEach(row -> row.setIssuerBank("B".repeat(1200)));
        when(context.mapper().selectIndexSourceSignature()).thenReturn(signature(5L, 7L));
        when(context.mapper().selectIndexPage(0L, 10)).thenReturn(rows);

        assertThatThrownBy(() -> context.index().publishFromReplica("g-next"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("byte budget");

        verify(context.hash(), never()).put(anyString(), eq("_generation"), anyString());
    }

    @Test
    void shouldReplicateOnlyBroadRangesAcrossThreeDigitShards() {
        List<CardBinRangeIndex.IndexCoordinate> dense = CardBinRangeIndex.indexCoordinates(
                55_578_282_800L, 55_578_282_899L);
        List<CardBinRangeIndex.IndexCoordinate> broad = CardBinRangeIndex.indexCoordinates(
                40_000_000_000L, 49_999_999_999L);

        assertThat(dense).containsExactly(
                new CardBinRangeIndex.IndexCoordinate("555", "9:555782828"));
        assertThat(broad).hasSize(100);
        assertThat(broad.get(0)).isEqualTo(
                new CardBinRangeIndex.IndexCoordinate("400", "3:400"));
        assertThat(broad.get(99)).isEqualTo(
                new CardBinRangeIndex.IndexCoordinate("499", "3:499"));
    }

    private List<Object> hashValues(String generation, String payload) {
        List<Object> values = new ArrayList<>();
        values.add(generation);
        values.add(payload);
        while (values.size() < 10) {
            values.add(null);
        }
        return values;
    }

    private void publicationRedis(TestContext context) {
        when(context.values().setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(context.redis().unlink(anyString())).thenReturn(true);
        when(context.redis().execute(eq(PaymentRedisScripts.cardBinBucketAppendV1()),
                anyList(), any(Object[].class))).thenReturn(1L);
        when(context.redis().execute(eq(PaymentRedisScripts.tokenLeaseRenewV1()),
                anyList(), anyString(), anyString())).thenReturn(1L);
        when(context.redis().execute(eq(PaymentRedisScripts.tokenLeaseReleaseV1()),
                anyList(), anyString())).thenReturn(1L);
    }

    private TestContext context() {
        return context(20, 4096, 100_000);
    }

    @SuppressWarnings("unchecked")
    private TestContext context(int batchEntries, int batchBytes, long snapshotBytes) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        CardBinLookupMapper mapper = mock(CardBinLookupMapper.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForHash()).thenReturn(hash);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        CardBinRangeIndex index = new CardBinRangeIndex(
                redis, Optional.empty(), properties, mapper, generationStore,
                10, 20, 100, batchEntries, batchBytes, snapshotBytes, Duration.ofMinutes(5));
        return new TestContext(index, redis, values, hash, mapper, generationStore);
    }

    private CardBinRangeIndexEntry entry(Long id,
                                         int binLength,
                                         String brand,
                                         LocalDateTime updateTime) {
        return new CardBinRangeIndexEntry(id, 51234500000L, 51234599999L, binLength,
                brand, null, "CREDIT", null, null, null, null, null, null,
                null, null, updateTime);
    }

    private CardBinRangeDO row() {
        return row(1L, 51234500000L, 51234599999L, 6);
    }

    private CardBinRangeDO row(long id, long start, long end, int binLength) {
        CardBinRangeDO row = new CardBinRangeDO();
        row.setId(id);
        row.setCardBinStart(start);
        row.setCardBinEnd(end);
        row.setBinLength(binLength);
        row.setCardBrand("MASTERCARD");
        row.setUpdateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        return row;
    }

    private CardBinIndexSourceSignatureDO signature(long rows, long checksum) {
        CardBinIndexSourceSignatureDO signature = new CardBinIndexSourceSignatureDO();
        signature.setRowCount(rows);
        signature.setChecksum(checksum);
        return signature;
    }

    private record TestContext(CardBinRangeIndex index,
                               StringRedisTemplate redis,
                               ValueOperations<String, String> values,
                               HashOperations<String, Object, Object> hash,
                               CardBinLookupMapper mapper,
                               RedisCacheGenerationStore generationStore) {
    }
}
