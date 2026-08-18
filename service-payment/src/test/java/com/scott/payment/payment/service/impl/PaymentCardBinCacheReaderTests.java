package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import com.scott.payment.payment.mapper.PaymentCardBinRangeMapper;
import com.scott.payment.payment.model.PaymentCardBinCacheEntry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCardBinCacheReaderTests {

    @Test
    void shouldQueryDatabaseOnlyOnceForSameElevenDigitPrefix() {
        PaymentCardBinRangeMapper mapper = mock(PaymentCardBinRangeMapper.class);
        PaymentCardBinRangeDO row = new PaymentCardBinRangeDO();
        row.setId(90001L);
        row.setBinLength(6);
        row.setCardBrand("MASTERCARD");
        row.setIssuerCountryAlpha2("AE");
        when(mapper.selectBestMatch(anyList(), eq(51234500000L))).thenReturn(row);

        try (AnnotationConfigApplicationContext context = cacheContext(mapper)) {
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
        when(mapper.selectBestMatch(anyList(), eq(99999900000L))).thenReturn(null);

        try (AnnotationConfigApplicationContext context = cacheContext(mapper)) {
            PaymentCardBinCacheReader reader = context.getBean(PaymentCardBinCacheReader.class);

            assertThat(reader.findByPrefix("99999900000").getMatched()).isFalse();
            assertThat(reader.findByPrefix("99999900000").getMatched()).isFalse();
            verify(mapper, times(1)).selectBestMatch(anyList(), eq(99999900000L));
        }
    }

    private AnnotationConfigApplicationContext cacheContext(PaymentCardBinRangeMapper mapper) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(CacheTestConfiguration.class);
        context.registerBean(PaymentCardBinRangeMapper.class, () -> mapper);
        context.registerBean(CacheManager.class,
                () -> new ConcurrentMapCacheManager(PaymentCacheNames.CARD_BIN));
        context.registerBean(PaymentCardBinCacheReader.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheTestConfiguration {
    }
}
