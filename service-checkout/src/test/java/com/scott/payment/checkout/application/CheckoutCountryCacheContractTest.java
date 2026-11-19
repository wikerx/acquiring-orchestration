package com.scott.payment.checkout.application;

import com.scott.payment.checkout.controller.CheckoutCountryConfigController;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.db.iso.service.impl.IsoDictionaryServiceImpl;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 由实际收银台入口贯穿 ISO 字典服务，验证暖缓存并发请求不会访问国家表。 */
class CheckoutCountryCacheContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void concurrentCheckoutCountryRequestsUseSharedRedisSnapshotWithoutDatabaseReads() throws Exception {
        var countries = mock(IsoCountryMapper.class);
        var currencies = mock(IsoCurrencyMapper.class);
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        var keys = mock(PaymentRedisKeyResolver.class);
        when(redis.opsForValue()).thenReturn(values);
        when(keys.businessKey("iso", "country", "all")).thenReturn("isolated:iso:country:all");
        when(values.get("isolated:iso:country:all")).thenReturn(JsonUtils.toJsonString(List.of(
                new IsoCountryInfo("US", "USA", "840", "United States", "United States", "美国", "NA", "北美洲", "🇺🇸", "en", "English", "英语", "USD"))));
        var beans = new StaticListableBeanFactory();
        beans.addBean("redis", redis);
        beans.addBean("keys", keys);
        var dictionary = new IsoDictionaryServiceImpl(countries, currencies,
                beans.getBeanProvider(StringRedisTemplate.class),
                beans.getBeanProvider(PaymentRedisKeyResolver.class),
                beans.getBeanProvider(com.scott.payment.component.core.cache.CacheInvalidationGuard.class),
                beans.getBeanProvider(com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator.class));
        var endpoint = new CheckoutCountryConfigController(new CheckoutCountryConfigApplicationService(dictionary));
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = IntStream.range(0, 100)
                    .mapToObj(i -> (Callable<String>) () -> JsonUtils.toJsonString(endpoint.listCountries()))
                    .toList();
            for (var result : pool.invokeAll(calls)) {
                assertThat(result.get()).contains("USA", "United States");
            }
        } finally {
            pool.shutdownNow();
        }
        verify(values, times(100)).get("isolated:iso:country:all");
        verifyNoInteractions(countries, currencies);
        verify(values, never()).set(anyString(), anyString());
    }
}
