package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.reference.cache.CardBinCacheReader;
import com.scott.payment.component.db.reference.model.CardBinLookupCacheEntry;
import com.scott.payment.component.db.reference.model.CardBinLookupResult;
import com.scott.payment.component.db.reference.model.CardBinLookupSnapshot;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataCacheReaderTest
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 商户基础数据缓存命中、代际、时间边界和故障回源测试。
 * @status : create
 */
class OpenApiReferenceDataCacheReaderTest {

    @Test
    void shouldCacheNormalizedIpAndMissWithoutRepeatingDatabaseLookup() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        when(lookupService.lookupIp("8.8.8.8"))
                .thenReturn(IpLookupResult.miss("8.8.8.8", "IPV4"));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.empty());

        assertThat(reader.lookupIp("008.008.008.008").matched()).isFalse();
        assertThat(reader.lookupIp("8.8.8.8").matched()).isFalse();
        verify(lookupService).lookupIp("8.8.8.8");
    }

    @Test
    void shouldReloadCardBinWhenAdminPublishesNewGeneration() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("card-bin-range"))
                .thenReturn(RedisCacheGenerationState.active("g-1"),
                        RedisCacheGenerationState.active("g-1"),
                        RedisCacheGenerationState.active("g-2"));
        when(lookupService.lookupCardBinSnapshot("41111100000"))
                .thenReturn(new CardBinLookupSnapshot(cardBinHit("41111100000"), null, null));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.of(generationStore));

        reader.lookupCardBin("411111");
        reader.lookupCardBin("411111");
        reader.lookupCardBin("411111");

        verify(lookupService, times(2)).lookupCardBinSnapshot("41111100000");
    }

    @Test
    void shouldReadCompletePaymentPrewarmedElevenDigitResultWithoutDatabaseLookup() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        CacheManager caches = new ConcurrentMapCacheManager();
        String cardBin = "51234500000";
        CardBinLookupResult result = new CardBinLookupResult(true, cardBin, 6, "MASTERCARD",
                "WORLD", "CREDIT", "GOLD", "United Arab Emirates", "AE", "ARE", "784", "Example Bank");
        Cache paymentCache = caches.getCache(PaymentCacheNames.CARD_BIN);
        assertThat(paymentCache).isNotNull();
        paymentCache.put(cardBin, new CardBinLookupCacheEntry("g-1",
                CardBinLookupCacheEntry.FORMAT_VERSION, result, null));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, caches, Optional.of(activeGeneration("g-1")));

        assertThat(reader.lookupCardBin(cardBin)).isEqualTo(result);
        verify(lookupService, org.mockito.Mockito.never()).lookupCardBinSnapshot(anyString());
    }

    @Test
    void shouldPadShortBinAndReadPaymentCacheWithoutDatabaseLookup() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        CacheManager caches = new ConcurrentMapCacheManager();
        String prefix = "51234500000";
        caches.getCache(PaymentCacheNames.CARD_BIN).put(prefix, new CardBinLookupCacheEntry("g-1",
                CardBinLookupCacheEntry.FORMAT_VERSION, cardBinHit(prefix), null));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, caches, Optional.of(activeGeneration("g-1")));

        CardBinLookupResult result = reader.lookupCardBin("512345");

        assertThat(result.matched()).isTrue();
        assertThat(result.cardBin()).isEqualTo("512345");
        assertThat(result.cardType()).isEqualTo("CREDIT");
        verify(lookupService, org.mockito.Mockito.never()).lookupCardBinSnapshot(anyString());
    }

    @Test
    void shouldReturnPlatformBrandWithoutClaimingDatabaseMatch() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        when(lookupService.lookupCardBinSnapshot("41111100000"))
                .thenReturn(new CardBinLookupSnapshot(CardBinLookupResult.miss("41111100000"), null, null));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.of(activeGeneration("g-1")));

        CardBinLookupResult result = reader.lookupCardBin("411111");

        assertThat(result.matched()).isFalse();
        assertThat(result.cardBin()).isEqualTo("411111");
        assertThat(result.cardBrand()).isEqualTo("VISA");
        assertThat(result.issuerBank()).isNull();
        verify(lookupService).lookupCardBinSnapshot("41111100000");
    }

    @Test
    void shouldTruncateLongInputBeforeCacheLookupAndResponse() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        String prefix = "51234567890";
        when(lookupService.lookupCardBinSnapshot(prefix))
                .thenReturn(new CardBinLookupSnapshot(cardBinHit(prefix), null, null));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.of(activeGeneration("g-1")));

        CardBinLookupResult result = reader.lookupCardBin("5123456789012345");

        assertThat(result.cardBin()).isEqualTo(prefix);
        verify(lookupService).lookupCardBinSnapshot(prefix);
    }

    @Test
    void shouldBuildElevenDigitSharedCacheFromSlaveAndIsolateOldKey() {
        String cardBin = "51234500000";
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        when(lookupService.lookupCardBinSnapshot(cardBin))
                .thenReturn(new CardBinLookupSnapshot(cardBinHit(cardBin), null, null));
        CacheManager caches = new ConcurrentMapCacheManager();
        Cache paymentCache = caches.getCache(PaymentCacheNames.CARD_BIN);
        assertThat(paymentCache).isNotNull();
        paymentCache.put("g-1:" + cardBin, new CardBinLookupCacheEntry("g-1", 2,
                CardBinLookupResult.miss(cardBin), LocalDateTime.now().plusMinutes(10)));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, caches, Optional.of(activeGeneration("g-1")));

        assertThat(reader.lookupCardBin(cardBin).matched()).isTrue();
        assertThat(reader.lookupCardBin(cardBin).matched()).isTrue();
        verify(lookupService).lookupCardBinSnapshot(cardBin);
        assertThat(paymentCache.get(cardBin)).isNotNull();
    }

    @Test
    void shouldReloadElevenDigitResultAfterGenerationOrBusinessBoundaryChanges() {
        String cardBin = "51234500000";
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        RedisCacheGenerationStore generations = mock(RedisCacheGenerationStore.class);
        when(generations.current("card-bin-range"))
                .thenReturn(RedisCacheGenerationState.active("g-1"),
                        RedisCacheGenerationState.active("g-1"),
                        RedisCacheGenerationState.active("g-2"));
        when(lookupService.lookupCardBinSnapshot(cardBin))
                .thenReturn(new CardBinLookupSnapshot(cardBinHit(cardBin), null,
                        LocalDateTime.now().minusSeconds(1)));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.of(generations));

        reader.lookupCardBin(cardBin);
        reader.lookupCardBin(cardBin);
        reader.lookupCardBin(cardBin);

        verify(lookupService, times(3)).lookupCardBinSnapshot(cardBin);
    }

    @Test
    void shouldUseSlaveWhenElevenDigitGenerationCannotBeRead() {
        String cardBin = "51234500000";
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        when(lookupService.lookupCardBinSnapshot(cardBin))
                .thenReturn(new CardBinLookupSnapshot(CardBinLookupResult.miss(cardBin), null, null));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.of(activePendingGeneration()));

        assertThat(reader.lookupCardBin(cardBin).matched()).isFalse();
        verify(lookupService).lookupCardBinSnapshot(cardBin);
    }

    @Test
    void shouldNotCacheElevenDigitMissPastFutureEffectiveTime() {
        String cardBin = "99999900000";
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        when(lookupService.lookupCardBinSnapshot(cardBin))
                .thenReturn(new CardBinLookupSnapshot(CardBinLookupResult.miss(cardBin), null,
                        LocalDateTime.now().minusSeconds(1)));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.of(activeGeneration("g-1")));

        assertThat(reader.lookupCardBin(cardBin).matched()).isFalse();
        assertThat(reader.lookupCardBin(cardBin).matched()).isFalse();
        verify(lookupService, times(2)).lookupCardBinSnapshot(cardBin);
    }

    @Test
    void shouldNotCacheAcrossExpiryOrFutureActivation() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("card-bin-range"))
                .thenReturn(RedisCacheGenerationState.active("g-1"));
        when(lookupService.lookupCardBinSnapshot("41111100000"))
                .thenReturn(new CardBinLookupSnapshot(cardBinHit("41111100000"),
                        LocalDateTime.now().minusSeconds(1), null));
        when(lookupService.lookupCardBinSnapshot("51111100000"))
                .thenReturn(new CardBinLookupSnapshot(CardBinLookupResult.miss("51111100000"),
                        null, LocalDateTime.now().minusSeconds(1)));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.of(generationStore));

        reader.lookupCardBin("411111");
        reader.lookupCardBin("411111");
        reader.lookupCardBin("511111");
        reader.lookupCardBin("511111");

        verify(lookupService, times(2)).lookupCardBinSnapshot("41111100000");
        verify(lookupService, times(2)).lookupCardBinSnapshot("51111100000");
    }

    @Test
    void shouldBypassCacheWhileGenerationIsPending() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current("card-bin-range"))
                .thenReturn(RedisCacheGenerationState.pending());
        when(lookupService.lookupCardBinSnapshot("41111100000"))
                .thenReturn(new CardBinLookupSnapshot(CardBinLookupResult.miss("41111100000"), null, null));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.of(generationStore));

        reader.lookupCardBin("411111");
        reader.lookupCardBin("411111");

        verify(lookupService, times(2)).lookupCardBinSnapshot("41111100000");
    }

    @Test
    void shouldFallBackToDatabaseWhenCacheFails() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        when(lookupService.lookupIp("8.8.8.8"))
                .thenReturn(IpLookupResult.miss("8.8.8.8", "IPV4"));
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, cacheManager, Optional.empty());

        assertThat(reader.lookupIp("8.8.8.8").matched()).isFalse();
        verify(lookupService).lookupIp("8.8.8.8");
    }

    @Test
    void shouldRejectInvalidInputBeforeCacheAccess() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        OpenApiReferenceDataCacheReader reader = reader(
                lookupService, new ConcurrentMapCacheManager(), Optional.empty());

        assertThatThrownBy(() -> reader.lookupIp("example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.lookupCardBin("41111"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.lookupCardBin("41111A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSerializeRegisteredCacheValue() {
        var serializer = PaymentRedisSerializerFactory.create();
        var ipEntry = new OpenApiReferenceDataCacheEntry(
                IpLookupResult.miss("8.8.8.8", "IPV4"),
                LocalDateTime.now().plusSeconds(30));

        assertThat(serializer.deserialize(serializer.serialize(ipEntry))).isEqualTo(ipEntry);
        var sharedEntry = new CardBinLookupCacheEntry("g-1", CardBinLookupCacheEntry.FORMAT_VERSION,
                cardBinHit("51234500000"), null);
        assertThat(serializer.deserialize(serializer.serialize(sharedEntry))).isEqualTo(sharedEntry);
    }

    private OpenApiReferenceDataCacheReader reader(ReferenceDataLookupService lookupService,
                                                   CacheManager caches,
                                                   Optional<RedisCacheGenerationStore> generations) {
        CardBinCacheReader sharedReader = new CardBinCacheReader(lookupService, caches, generations);
        return new OpenApiReferenceDataCacheReader(lookupService, sharedReader, caches);
    }

    private RedisCacheGenerationStore activeGeneration(String generation) {
        RedisCacheGenerationStore store = mock(RedisCacheGenerationStore.class);
        when(store.current("card-bin-range")).thenReturn(RedisCacheGenerationState.active(generation));
        return store;
    }

    private RedisCacheGenerationStore activePendingGeneration() {
        RedisCacheGenerationStore store = mock(RedisCacheGenerationStore.class);
        when(store.current("card-bin-range")).thenReturn(RedisCacheGenerationState.pending());
        return store;
    }

    private CardBinLookupResult cardBinHit(String cardBin) {
        return new CardBinLookupResult(true, cardBin, 6, "VISA", null,
                "CREDIT", null, null, null, null, null, null);
    }
}
