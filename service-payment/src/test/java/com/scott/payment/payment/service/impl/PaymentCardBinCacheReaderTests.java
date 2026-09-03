package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.redis.generation.RedisCacheGenerationState;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import com.scott.payment.payment.mapper.PaymentCardBinRangeMapper;
import com.scott.payment.payment.model.PaymentCardBinCacheEntry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCardBinCacheReaderTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证卡 BIN 缓存主库回源、负缓存、有效期和 generation 切换后的隔离读取
 * @status : create
 */
class PaymentCardBinCacheReaderTests {

    /** BIN 缓存失效后的首次重建必须读主库，避免复制延迟重新缓存旧区间。 */
    @Test
    void shouldRebuildCardBinCacheFromMaster() throws Exception {
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(
                PaymentCardBinCacheReader.class.getMethod("findByPrefix", String.class),
                DS.class
        );

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
    }

    @Test
    void shouldQueryDatabaseOnlyOnceForSameElevenDigitPrefix() {
        PaymentCardBinRangeMapper mapper = mock(PaymentCardBinRangeMapper.class);
        RedisCacheGenerationStore generationStore = activeGeneration("g-1");
        PaymentCardBinRangeDO row = new PaymentCardBinRangeDO();
        row.setId(90001L);
        row.setBinLength(6);
        row.setCardBrand("MASTERCARD");
        row.setIssuerCountryAlpha2("AE");
        when(mapper.selectBestMatch(anyList(), eq(51234500000L))).thenReturn(row);

        try (AnnotationConfigApplicationContext context = cacheContext(mapper, generationStore)) {
            PaymentCardBinCacheReader reader = context.getBean(PaymentCardBinCacheReader.class);

            PaymentCardBinCacheEntry first = reader.findByPrefix("51234500000");
            PaymentCardBinCacheEntry second = reader.findByPrefix("51234500000");

            assertThat(second).isSameAs(first);
            assertThat(first.getCardBrand()).isEqualTo("MASTERCARD");
            assertThat(first.getIssuerCountryAlpha2()).isEqualTo("AE");
            verify(mapper, times(1)).selectBestMatch(
                    eq(List.of(51234500000L, 51234500000L, 51234500000L,
                            51234500000L, 51234500000L, 51234500000L)),
                    eq(51234500000L));
        }
    }

    @Test
    void shouldCacheDatabaseMiss() {
        PaymentCardBinRangeMapper mapper = mock(PaymentCardBinRangeMapper.class);
        RedisCacheGenerationStore generationStore = activeGeneration("g-1");
        when(mapper.selectBestMatch(anyList(), eq(99999900000L))).thenReturn(null);
        when(mapper.selectNextEffectiveTime(anyList(), eq(99999900000L))).thenReturn(null);

        try (AnnotationConfigApplicationContext context = cacheContext(mapper, generationStore)) {
            PaymentCardBinCacheReader reader = context.getBean(PaymentCardBinCacheReader.class);

            assertThat(reader.findByPrefix("99999900000").getMatched()).isFalse();
            assertThat(reader.findByPrefix("99999900000").getMatched()).isFalse();
            verify(mapper, times(1)).selectBestMatch(anyList(), eq(99999900000L));
            verify(mapper, times(1)).selectNextEffectiveTime(anyList(), eq(99999900000L));
        }
    }

    @Test
    void shouldUseSeparateCacheEntriesAfterGenerationChanges() {
        PaymentCardBinRangeMapper mapper = mock(PaymentCardBinRangeMapper.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current(PaymentCardBinCacheReader.CACHE_NAMESPACE))
                .thenReturn(RedisCacheGenerationState.active("g-1"),
                        RedisCacheGenerationState.active("g-2"));
        PaymentCardBinRangeDO row = matchedRow();
        when(mapper.selectBestMatch(anyList(), eq(51234500000L))).thenReturn(row);

        try (AnnotationConfigApplicationContext context = cacheContext(mapper, generationStore)) {
            PaymentCardBinCacheReader reader = context.getBean(PaymentCardBinCacheReader.class);

            reader.findByPrefix("51234500000");
            reader.findByPrefix("51234500000");

            verify(mapper, times(2)).selectBestMatch(anyList(), eq(51234500000L));
        }
    }

    @Test
    void shouldReloadExpiredMatchedEntryFromMaster() {
        PaymentCardBinRangeMapper mapper = mock(PaymentCardBinRangeMapper.class);
        RedisCacheGenerationStore generationStore = activeGeneration("g-1");
        PaymentCardBinRangeDO freshRow = matchedRow();
        freshRow.setCardBrand("VISA");
        when(mapper.selectBestMatch(anyList(), eq(51234500000L))).thenReturn(freshRow);

        try (AnnotationConfigApplicationContext context = cacheContext(mapper, generationStore)) {
            Cache cache = context.getBean(CacheManager.class).getCache(PaymentCacheNames.CARD_BIN);
            PaymentCardBinCacheEntry expired = PaymentCardBinCacheEntry.miss("51234500000");
            expired.setMatched(Boolean.TRUE);
            expired.setCardBrand("OLD");
            expired.setExpireTime(LocalDateTime.now().minusSeconds(1));
            assertThat(cache).isNotNull();
            cache.put("g-1:51234500000", expired);

            PaymentCardBinCacheEntry loaded = context.getBean(PaymentCardBinCacheReader.class)
                    .findByPrefix("51234500000");

            assertThat(loaded.getCardBrand()).isEqualTo("VISA");
            verify(mapper).selectBestMatch(anyList(), eq(51234500000L));
        }
    }

    @Test
    void shouldReloadMissAfterItsNextEffectiveTime() {
        PaymentCardBinRangeMapper mapper = mock(PaymentCardBinRangeMapper.class);
        RedisCacheGenerationStore generationStore = activeGeneration("g-1");
        when(mapper.selectBestMatch(anyList(), eq(99999900000L))).thenReturn(null);
        when(mapper.selectNextEffectiveTime(anyList(), eq(99999900000L)))
                .thenReturn(LocalDateTime.now().plusDays(1));

        try (AnnotationConfigApplicationContext context = cacheContext(mapper, generationStore)) {
            Cache cache = context.getBean(CacheManager.class).getCache(PaymentCacheNames.CARD_BIN_MISS);
            PaymentCardBinCacheEntry staleMiss = PaymentCardBinCacheEntry.miss("99999900000");
            staleMiss.setNextEffectiveTime(LocalDateTime.now().minusSeconds(1));
            assertThat(cache).isNotNull();
            cache.put("g-1:99999900000", staleMiss);

            PaymentCardBinCacheEntry loaded = context.getBean(PaymentCardBinCacheReader.class)
                    .findByPrefix("99999900000");

            assertThat(loaded.getNextEffectiveTime()).isAfter(LocalDateTime.now());
            verify(mapper).selectBestMatch(anyList(), eq(99999900000L));
            verify(mapper).selectNextEffectiveTime(anyList(), eq(99999900000L));
        }
    }

    private RedisCacheGenerationStore activeGeneration(String generation) {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(generationStore.current(PaymentCardBinCacheReader.CACHE_NAMESPACE))
                .thenReturn(RedisCacheGenerationState.active(generation));
        return generationStore;
    }

    private PaymentCardBinRangeDO matchedRow() {
        PaymentCardBinRangeDO row = new PaymentCardBinRangeDO();
        row.setId(90001L);
        row.setBinLength(6);
        row.setCardBrand("MASTERCARD");
        row.setIssuerCountryAlpha2("AE");
        return row;
    }

    private AnnotationConfigApplicationContext cacheContext(PaymentCardBinRangeMapper mapper,
                                                            RedisCacheGenerationStore generationStore) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(CacheTestConfiguration.class);
        context.registerBean(PaymentCardBinRangeMapper.class, () -> mapper);
        context.registerBean(RedisCacheGenerationStore.class, () -> generationStore);
        context.registerBean(CacheManager.class,
                () -> new ConcurrentMapCacheManager(
                        PaymentCacheNames.CARD_BIN,
                        PaymentCacheNames.CARD_BIN_MISS
                ));
        context.registerBean(PaymentCardBinCacheReader.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheTestConfiguration {
    }
}
