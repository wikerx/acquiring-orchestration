package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.reference.model.CardBinLookupCacheEntry;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.CardBinLookupSnapshot;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinCacheReaderTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 共享 BIN 缓存的从库回源、代际隔离、旧值隔离和时间边界测试。
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
    void shouldCacheCompleteResultForSameElevenDigitPrefix() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot("51234500000"))
                .thenReturn(new CardBinLookupSnapshot(hit("51234500000"), null, null));
        CacheManager caches = new ConcurrentMapCacheManager();
        CardBinCacheReader reader = reader(lookup, activeGeneration("g-1"), caches);

        CardBinLookupCacheEntry first = reader.findByPrefix("51234500000");
        CardBinLookupCacheEntry second = reader.findByPrefix("51234500000");

        assertThat(second).isSameAs(first);
        assertThat(first.result().cardBrand()).isEqualTo("MASTERCARD");
        assertThat(first.result().issuerBank()).isEqualTo("Example Bank");
        assertThat(first.validUntil()).isNull();
        assertThat(caches.getCache(PaymentCacheNames.CARD_BIN).get("51234500000")).isNotNull();
        verify(lookup).lookupCardBinSnapshot("51234500000");
    }

    @Test
    void shouldShareElevenDigitResultAcrossServiceReaders() {
        String cardBin = "51234500000";
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot(cardBin))
                .thenReturn(new CardBinLookupSnapshot(hit(cardBin), null, null));
        CacheManager caches = new ConcurrentMapCacheManager();
        RedisCacheGenerationStore generations = activeGeneration("g-1");
        CardBinCacheReader paymentReader = reader(lookup, generations, caches);
        CardBinCacheReader openApiReader = reader(lookup, generations, caches);

        assertThat(openApiReader.findByPrefix(cardBin).result())
                .isEqualTo(paymentReader.findByPrefix(cardBin).result());
        verify(lookup).lookupCardBinSnapshot(cardBin);
    }

    @Test
    void shouldReturnMissWithoutCachingAndIgnoreOldFormatKey() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot("99999900000"))
                .thenReturn(new CardBinLookupSnapshot(CardBinLookupResult.miss("99999900000"), null, null));
        CacheManager caches = new ConcurrentMapCacheManager();
        Cache hitCache = caches.getCache(PaymentCacheNames.CARD_BIN);
        assertThat(hitCache).isNotNull();
        hitCache.put("g-1:99999900000", new CardBinLookupCacheEntry("g-1",
                CardBinLookupCacheEntry.FORMAT_VERSION, hit("99999900000"), null));
        CardBinCacheReader reader = reader(lookup, activeGeneration("g-1"), caches);

        assertThat(reader.findByPrefix("99999900000").result().matched()).isFalse();
        assertThat(reader.findByPrefix("99999900000").result().matched()).isFalse();
        verify(lookup, times(2)).lookupCardBinSnapshot("99999900000");
        assertThat(hitCache.get("99999900000")).isNull();
    }

    @Test
    void shouldRemoveLegacyShortKeyWhenDatabaseHasNoMatch() {
        String cardBin = "99999900000";
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot(cardBin))
                .thenReturn(new CardBinLookupSnapshot(CardBinLookupResult.miss(cardBin), null, null));
        CacheManager caches = new ConcurrentMapCacheManager();
        Cache hitCache = caches.getCache(PaymentCacheNames.CARD_BIN);
        hitCache.put(cardBin, "legacy-value");
        CardBinCacheReader reader = reader(lookup, activeGeneration("g-1"), caches);

        assertThat(reader.findByPrefix(cardBin).result().matched()).isFalse();
        assertThat(hitCache.get(cardBin)).isNull();
    }

    @Test
    void shouldReplaceOldFormatValueAtShortKey() {
        String cardBin = "51234500000";
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot(cardBin))
                .thenReturn(new CardBinLookupSnapshot(hit(cardBin), null, null));
        CacheManager caches = new ConcurrentMapCacheManager();
        caches.getCache(PaymentCacheNames.CARD_BIN).put(cardBin,
                new CardBinLookupCacheEntry("g-1", 2, CardBinLookupResult.miss(cardBin), null));
        CardBinCacheReader reader = reader(lookup, activeGeneration("g-1"), caches);

        CardBinLookupCacheEntry result = reader.findByPrefix(cardBin);

        assertThat(result.result().matched()).isTrue();
        assertThat(result.formatVersion()).isEqualTo(CardBinLookupCacheEntry.FORMAT_VERSION);
        verify(lookup).lookupCardBinSnapshot(cardBin);
    }

    @Test
    void shouldReloadAfterGenerationChange() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        RedisCacheGenerationStore generations = mock(RedisCacheGenerationStore.class);
        when(generations.current("card-bin-range"))
                .thenReturn(RedisCacheGenerationState.active("g-1"),
                        RedisCacheGenerationState.active("g-2"));
        when(lookup.lookupCardBinSnapshot("51234500000"))
                .thenReturn(new CardBinLookupSnapshot(hit("51234500000"), null, null));
        CacheManager caches = new ConcurrentMapCacheManager();
        CardBinCacheReader reader = reader(lookup, generations, caches);

        reader.findByPrefix("51234500000");
        reader.findByPrefix("51234500000");

        verify(lookup, times(2)).lookupCardBinSnapshot("51234500000");
        CardBinLookupCacheEntry refreshed = caches.getCache(PaymentCacheNames.CARD_BIN)
                .get("51234500000", CardBinLookupCacheEntry.class);
        assertThat(refreshed.generation()).isEqualTo("g-2");
    }

    @Test
    void shouldReloadWhenTimeBoundaryHasPassed() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot("51234500000"))
                .thenReturn(new CardBinLookupSnapshot(hit("51234500000"),
                        LocalDateTime.now().minusSeconds(1), null));
        CardBinCacheReader reader = reader(lookup, activeGeneration("g-1"), new ConcurrentMapCacheManager());

        reader.findByPrefix("51234500000");
        reader.findByPrefix("51234500000");

        verify(lookup, times(2)).lookupCardBinSnapshot("51234500000");
    }

    @Test
    void shouldBypassSharedCacheWhileAdminPublicationIsPending() {
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot("51234500000"))
                .thenReturn(new CardBinLookupSnapshot(hit("51234500000"), null, null));
        RedisCacheGenerationStore generations = mock(RedisCacheGenerationStore.class);
        when(generations.current("card-bin-range")).thenReturn(RedisCacheGenerationState.pending());
        CardBinCacheReader reader = reader(lookup, generations, new ConcurrentMapCacheManager());

        reader.findByPrefix("51234500000");
        reader.findByPrefix("51234500000");

        verify(lookup, times(2)).lookupCardBinSnapshot("51234500000");
    }

    @Test
    void shouldBypassSharedCacheWhenGenerationReadFails() {
        String cardBin = "51234500000";
        ReferenceDataLookupService lookup = mock(ReferenceDataLookupService.class);
        when(lookup.lookupCardBinSnapshot(cardBin))
                .thenReturn(new CardBinLookupSnapshot(hit(cardBin), null, null));
        RedisCacheGenerationStore generations = mock(RedisCacheGenerationStore.class);
        when(generations.current("card-bin-range")).thenThrow(new IllegalStateException("Redis unavailable"));
        CacheManager caches = new ConcurrentMapCacheManager();
        CardBinCacheReader reader = reader(lookup, generations, caches);

        reader.findByPrefix(cardBin);
        reader.findByPrefix(cardBin);

        verify(lookup, times(2)).lookupCardBinSnapshot(cardBin);
        assertThat(caches.getCache(PaymentCacheNames.CARD_BIN)
                .get(cardBin)).isNull();
    }

    private CardBinCacheReader reader(ReferenceDataLookupService lookup,
                                             RedisCacheGenerationStore generations,
                                             CacheManager caches) {
        return new CardBinCacheReader(lookup, caches, Optional.of(generations));
    }

    private RedisCacheGenerationStore activeGeneration(String generation) {
        RedisCacheGenerationStore store = mock(RedisCacheGenerationStore.class);
        when(store.current("card-bin-range")).thenReturn(RedisCacheGenerationState.active(generation));
        return store;
    }

    private CardBinLookupResult hit(String prefix) {
        return new CardBinLookupResult(true, prefix, 6, "MASTERCARD", "WORLD",
                "CREDIT", "GOLD", "United Arab Emirates", "AE", "ARE", "784", "Example Bank");
    }
}
