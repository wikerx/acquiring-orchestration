package com.scott.payment.openapi.security;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.entity.MerchantIpWhitelistDO;
import com.scott.payment.component.db.auth.mapper.MerchantIpWhitelistMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiAccessPolicyCacheServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 访问策略缓存代理测试。
 * @status : create
 */
class MerchantOpenApiAccessPolicyCacheServiceTests {

    @Test
    void shouldCachePolicyByPositionalMethodArgument() {
        initializeTableMetadata();
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(whitelistMapper.selectList(any())).thenReturn(List.of());

        try (AnnotationConfigApplicationContext context =
                     context(whitelistMapper, invalidationGuard)) {
            MerchantOpenApiAccessPolicyCacheService service =
                    context.getBean(MerchantOpenApiAccessPolicyCacheService.class);
            service.findPolicy("200045");
            service.findPolicy("200045");

            verify(whitelistMapper, times(1)).selectList(any());
            Cache cache = context.getBean(CacheManager.class)
                    .getCache(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS);
            assertThat(cache).isNotNull();
            assertThat(cache.get("200045")).isNotNull();
        }
    }

    @Test
    void shouldBypassStalePolicyWhenInvalidationIsPending() {
        initializeTableMetadata();
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(invalidationGuard.isPending(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        )).thenReturn(true);
        when(whitelistMapper.selectList(any())).thenReturn(List.of(whitelist("203.0.113.10")));

        try (AnnotationConfigApplicationContext context =
                     context(whitelistMapper, invalidationGuard)) {
            policyCache(context).put("200045", emptyPolicy());

            MerchantOpenApiAccessPolicy policy = context
                    .getBean(MerchantOpenApiAccessPolicyCacheService.class)
                    .findPolicy(" 200045 ");

            assertThat(policy.getAllowedIps()).containsExactly("203.0.113.10");
            verify(whitelistMapper).selectList(any());
        }
    }

    @Test
    void shouldBypassStalePolicyWhenGuardCheckFails() {
        initializeTableMetadata();
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(invalidationGuard)
                .isPending(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS, "200045");
        when(whitelistMapper.selectList(any())).thenReturn(List.of(whitelist("203.0.113.10")));

        try (AnnotationConfigApplicationContext context =
                     context(whitelistMapper, invalidationGuard)) {
            policyCache(context).put("200045", emptyPolicy());

            MerchantOpenApiAccessPolicy policy = context
                    .getBean(MerchantOpenApiAccessPolicyCacheService.class)
                    .findPolicy("200045");

            assertThat(policy.getAllowedIps()).containsExactly("203.0.113.10");
            verify(whitelistMapper).selectList(any());
        }
    }

    @Test
    void shouldRejectInsteadOfUsingStalePolicyWhenMasterReadFails() {
        initializeTableMetadata();
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(invalidationGuard.isPending(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        )).thenReturn(true);
        when(whitelistMapper.selectList(any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        try (AnnotationConfigApplicationContext context =
                     context(whitelistMapper, invalidationGuard)) {
            policyCache(context).put("200045", emptyPolicy());

            assertThatThrownBy(() -> context
                    .getBean(MerchantOpenApiAccessPolicyCacheService.class)
                    .findPolicy("200045"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("database unavailable");
        }
    }

    @Test
    void shouldReloadWhenGateBecomesPendingDuringCacheRead() {
        initializeTableMetadata();
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(invalidationGuard.isPending(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        )).thenReturn(false, true);
        when(whitelistMapper.selectList(any())).thenReturn(List.of(whitelist("203.0.113.10")));

        try (AnnotationConfigApplicationContext context =
                     context(whitelistMapper, invalidationGuard)) {
            policyCache(context).put("200045", emptyPolicy());

            MerchantOpenApiAccessPolicy policy = context
                    .getBean(MerchantOpenApiAccessPolicyCacheService.class)
                    .findPolicy("200045");

            assertThat(policy.getAllowedIps()).containsExactly("203.0.113.10");
        }
    }

    @Test
    void shouldRouteCachedAndFreshLoadsToMaster() throws NoSuchMethodException {
        assertMasterRoute("findCached");
        assertMasterRoute("findFresh");
    }

    private AnnotationConfigApplicationContext context(
            MerchantIpWhitelistMapper whitelistMapper,
            CacheInvalidationGuard invalidationGuard) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(CachingConfiguration.class);
        context.registerBean(MerchantIpWhitelistMapper.class, () -> whitelistMapper);
        context.registerBean(CacheInvalidationGuard.class, () -> invalidationGuard);
        context.registerBean(MerchantOpenApiAccessPolicyCacheReader.class);
        context.registerBean(MerchantOpenApiAccessPolicyCacheService.class);
        context.refresh();
        return context;
    }

    private void initializeTableMetadata() {
        initializeTableMetadata(MerchantIpWhitelistDO.class);
    }

    private void initializeTableMetadata(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private MerchantIpWhitelistDO whitelist(String ip) {
        MerchantIpWhitelistDO whitelist = new MerchantIpWhitelistDO();
        whitelist.setMerchantId("200045");
        whitelist.setIpValue(ip);
        whitelist.setStatus(1);
        return whitelist;
    }

    private MerchantOpenApiAccessPolicy emptyPolicy() {
        return new MerchantOpenApiAccessPolicy();
    }

    private Cache policyCache(AnnotationConfigApplicationContext context) {
        Cache cache = context.getBean(CacheManager.class)
                .getCache(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS);
        assertThat(cache).isNotNull();
        return cache;
    }

    private void assertMasterRoute(String methodName) throws NoSuchMethodException {
        DS dataSource = MerchantOpenApiAccessPolicyCacheReader.class
                .getMethod(methodName, String.class)
                .getAnnotation(DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CachingConfiguration {

        @org.springframework.context.annotation.Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS);
        }
    }
}
