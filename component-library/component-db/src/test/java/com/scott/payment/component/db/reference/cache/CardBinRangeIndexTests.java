package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.reference.entity.CardBinIndexSourceSignatureDO;
import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import com.scott.payment.component.db.reference.mapper.CardBinLookupMapper;
import com.scott.payment.component.db.reference.model.CardBinRangeIndexEntry;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Card BIN A/B 快照的最优匹配、槽切换和源数据变化保护。 */
class CardBinRangeIndexTests {

    @Test
    void shouldChooseMostPreciseNewestAndLargestIdMatchingCandidate() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        List<CardBinRangeIndexEntry> candidates = List.of(
                entry(1L, 6, "VISA", LocalDateTime.of(2026, 1, 1, 0, 0)),
                entry(2L, 8, "OLD", LocalDateTime.of(2026, 1, 2, 0, 0)),
                entry(3L, 8, "NEW", LocalDateTime.of(2026, 1, 3, 0, 0)),
                entry(4L, 8, "LARGEST_ID", LocalDateTime.of(2026, 1, 3, 0, 0))
        );
        when(redis.execute(eq(PaymentRedisScripts.cardBinBucketReadV1()), anyList(), eq("512345")))
                .thenReturn(JsonUtils.toJsonString(candidates));
        CardBinRangeIndex index = index(redis, mock(CardBinLookupMapper.class));

        var lookup = index.lookup("51234567890");

        assertThat(lookup.available()).isTrue();
        assertThat(lookup.result().matched()).isTrue();
        assertThat(lookup.result().binLength()).isEqualTo(8);
        assertThat(lookup.result().cardBrand()).isEqualTo("LARGEST_ID");
    }

    @Test
    void shouldBuildInactiveSlotAndSwitchPointerOnlyAfterStableReplicaRead() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("a");
        when(redis.execute(eq(PaymentRedisScripts.cardBinSnapshotBeginV1()),
                anyList(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(eq(PaymentRedisScripts.cardBinBucketAppendV1()),
                anyList(), anyString(), anyString(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(eq(PaymentRedisScripts.cardBinSnapshotCommitV1()),
                anyList(), anyString(), eq("b"))).thenReturn(1L);
        CardBinLookupMapper mapper = mock(CardBinLookupMapper.class);
        when(mapper.selectIndexSourceSignature()).thenReturn(signature(1L, 7L), signature(1L, 7L));
        when(mapper.selectIndexPage(0L, 10)).thenReturn(List.of(row()));
        CardBinRangeIndex index = index(redis, mapper);

        assertThat(index.publishFromReplica()).isEqualTo("b");

        verify(redis).execute(eq(PaymentRedisScripts.cardBinSnapshotCommitV1()),
                argThat(keys -> keys.size() == 3
                        && keys.get(0).endsWith(":active")
                        && keys.get(2).endsWith(":slot:a")),
                anyString(), eq("b"));
    }

    @Test
    void shouldRetainOldActiveSlotWhenReplicaChangesDuringBuild() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("a");
        when(redis.execute(eq(PaymentRedisScripts.cardBinSnapshotBeginV1()),
                anyList(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(eq(PaymentRedisScripts.cardBinBucketAppendV1()),
                anyList(), anyString(), anyString(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(eq(PaymentRedisScripts.cardBinSnapshotAbortV1()),
                anyList(), anyString())).thenReturn(1L);
        CardBinLookupMapper mapper = mock(CardBinLookupMapper.class);
        when(mapper.selectIndexSourceSignature()).thenReturn(signature(1L, 7L), signature(1L, 8L));
        when(mapper.selectIndexPage(0L, 10)).thenReturn(List.of(row()));
        CardBinRangeIndex index = index(redis, mapper);

        assertThatThrownBy(index::publishFromReplica)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed while building");

        verify(redis, never()).execute(eq(PaymentRedisScripts.cardBinSnapshotCommitV1()),
                anyList(), anyString(), anyString());
        verify(redis, atLeastOnce()).execute(eq(PaymentRedisScripts.cardBinSnapshotAbortV1()),
                anyList(), anyString());
    }

    @Test
    void shouldSplitLargeRangeIntoBoundedWriteBatches() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("a");
        when(redis.execute(eq(PaymentRedisScripts.cardBinSnapshotBeginV1()),
                anyList(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(eq(PaymentRedisScripts.cardBinBucketAppendV1()),
                anyList(), any(Object[].class))).thenReturn(2L, 1L);
        when(redis.execute(eq(PaymentRedisScripts.cardBinSnapshotCommitV1()),
                anyList(), anyString(), eq("b"))).thenReturn(1L);
        CardBinLookupMapper mapper = mock(CardBinLookupMapper.class);
        CardBinRangeDO spanning = row();
        spanning.setCardBinEnd(51234799999L);
        when(mapper.selectIndexSourceSignature()).thenReturn(signature(1L, 7L), signature(1L, 7L));
        when(mapper.selectIndexPage(0L, 10)).thenReturn(List.of(spanning));
        CardBinRangeIndex index = index(redis, mapper, 2, 4096, 100_000);

        assertThat(index.publishFromReplica()).isEqualTo("b");

        verify(redis, times(2)).execute(eq(PaymentRedisScripts.cardBinBucketAppendV1()),
                anyList(), any(Object[].class));
    }

    @Test
    void shouldAbortBeforeWritingWhenSnapshotByteBudgetIsExceeded() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("a");
        when(redis.execute(eq(PaymentRedisScripts.cardBinSnapshotBeginV1()),
                anyList(), anyString(), anyString())).thenReturn(1L);
        when(redis.execute(eq(PaymentRedisScripts.cardBinSnapshotAbortV1()),
                anyList(), anyString())).thenReturn(1L);
        CardBinLookupMapper mapper = mock(CardBinLookupMapper.class);
        CardBinRangeDO spanning = row();
        spanning.setCardBinEnd(51236599999L);
        when(mapper.selectIndexSourceSignature()).thenReturn(signature(1L, 7L));
        when(mapper.selectIndexPage(0L, 10)).thenReturn(List.of(spanning));
        CardBinRangeIndex index = index(redis, mapper, 100, 4096, 4096);

        assertThatThrownBy(index::publishFromReplica)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("byte budget");

        verify(redis, never()).execute(eq(PaymentRedisScripts.cardBinSnapshotCommitV1()),
                anyList(), anyString(), anyString());
    }

    private CardBinRangeIndex index(StringRedisTemplate redis, CardBinLookupMapper mapper) {
        return index(redis, mapper, 20, 4096, 100_000);
    }

    private CardBinRangeIndex index(StringRedisTemplate redis,
                                    CardBinLookupMapper mapper,
                                    int batchEntries,
                                    int batchBytes,
                                    long snapshotBytes) {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return new CardBinRangeIndex(redis, properties, mapper, 10, 20, 100,
                batchEntries, batchBytes, snapshotBytes, Duration.ofMinutes(5));
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
        CardBinRangeDO row = new CardBinRangeDO();
        row.setId(1L);
        row.setCardBinStart(51234500000L);
        row.setCardBinEnd(51234599999L);
        row.setBinLength(6);
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
}
