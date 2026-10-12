package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.CardBinLookupSnapshot;
import com.scott.payment.component.db.reference.model.CardBinRangeIndexLookup;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinCacheReaderTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 共享 Card BIN 查询入口的输入规范、快照命中、权威未命中和单次从库降级测试
 * @status : update
 */
class CardBinCacheReaderTests {

    @Test
    void shouldUsePaymentElevenDigitPrefixRules() {
        assertThat(CardBinCacheReader.toElevenDigitPrefix("512345")).isEqualTo("51234500000");
        assertThat(CardBinCacheReader.toElevenDigitPrefix("51234567890")).isEqualTo("51234567890");
        assertThat(CardBinCacheReader.toElevenDigitPrefix("5123456789012345")).isEqualTo("51234567890");
        assertThatThrownBy(() -> CardBinCacheReader.toElevenDigitPrefix("51234"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CardBinCacheReader.toElevenDigitPrefix("51234A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnSharedSnapshotHitWithoutDatabaseLookup() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        CardBinRangeIndex index = mock(CardBinRangeIndex.class);
        when(index.lookup("51234500000"))
                .thenReturn(CardBinRangeIndexLookup.available(hit("51234500000")));
        CardBinCacheReader reader = new CardBinCacheReader(lookup, Optional.of(index));

        assertThat(reader.findByPrefix("51234500000").result().issuerBank())
                .isEqualTo("Example Bank");

        verify(lookup, never()).lookupCardBinSnapshot("51234500000");
    }

    @Test
    void shouldTreatSnapshotMissAsAuthoritativeWithoutCreatingQueryCache() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        CardBinRangeIndex index = mock(CardBinRangeIndex.class);
        when(index.lookup("99999900000")).thenReturn(CardBinRangeIndexLookup.available(
                CardBinLookupResult.miss("99999900000")));
        CardBinCacheReader reader = new CardBinCacheReader(lookup, Optional.of(index));

        assertThat(reader.findByPrefix("99999900000").result().matched()).isFalse();

        verify(lookup, never()).lookupCardBinSnapshot("99999900000");
    }

    @Test
    void shouldExecuteOneReplicaLookupWhenSnapshotIsUnavailable() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        CardBinRangeIndex index = mock(CardBinRangeIndex.class);
        when(index.lookup("51234500000")).thenReturn(CardBinRangeIndexLookup.unavailable());
        when(lookup.lookupCardBinSnapshot("51234500000"))
                .thenReturn(new CardBinLookupSnapshot(hit("51234500000"), null, null));
        CardBinCacheReader reader = new CardBinCacheReader(lookup, Optional.of(index));

        assertThat(reader.findByPrefix("51234500000").result().matched()).isTrue();

        verify(lookup).lookupCardBinSnapshot("51234500000");
    }

    @Test
    void shouldUseReplicaDirectlyWhenRedisComponentIsAbsent() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot("51234500000"))
                .thenReturn(new CardBinLookupSnapshot(hit("51234500000"), null, null));
        CardBinCacheReader reader = new CardBinCacheReader(lookup, Optional.empty());

        assertThat(reader.findByPrefix("51234500000").result().matched()).isTrue();
        verify(lookup).lookupCardBinSnapshot("51234500000");
    }

    private CardBinLookupResult hit(String prefix) {
        return new CardBinLookupResult(true, prefix, 6, "MASTERCARD", "WORLD",
                "CREDIT", "GOLD", "United Arab Emirates", "AE", "ARE", "784", "Example Bank");
    }
}
