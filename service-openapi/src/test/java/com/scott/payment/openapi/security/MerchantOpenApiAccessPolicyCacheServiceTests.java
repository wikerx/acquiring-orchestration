package com.scott.payment.openapi.security;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.entity.MerchantIpWhitelistDO;
import com.scott.payment.component.db.auth.entity.MerchantOpenApiAccessConfigDO;
import com.scott.payment.component.db.auth.mapper.MerchantIpWhitelistMapper;
import com.scott.payment.component.db.auth.mapper.MerchantOpenApiAccessConfigMapper;
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
 * 商户 OpenAPI 访问策略缓存代理测试。
 */
class MerchantOpenApiAccessPolicyCacheServiceTests {

    @Test
    void shouldCachePolicyByPositionalMethodArgument() {
        initializeTableMetadata();
        MerchantOpenApiAccessConfigMapper accessConfigMapper = mock(MerchantOpenApiAccessConfigMapper.class);
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(accessConfigMapper.selectOne(any())).thenReturn(null);

        try (AnnotationConfigApplicationContext context =
                     context(accessConfigMapper, whitelistMapper, invalidationGuard)) {
            MerchantOpenApiAccessPolicyCacheService service =
                    context.getBean(MerchantOpenApiAccessPolicyCacheService.class);
            service.findPolicy("200045");
            service.findPolicy("200045");

            verify(accessConfigMapper, times(1)).selectOne(any());
            Cache cache = context.getBean(CacheManager.class)
                    .getCache(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS);
            assertThat(cache).isNotNull();
            assertThat(cache.get("200045")).isNotNull();
        }
    }

    @Test
    void shouldBypassStalePolicyWhenInvalidationIsPending() {
        initializeTableMetadata();
        MerchantOpenApiAccessConfigMapper accessConfigMapper = mock(MerchantOpenApiAccessConfigMapper.class);
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(invalidationGuard.isPending(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        )).thenReturn(true);
        when(accessConfigMapper.selectOne(any())).thenReturn(enabledConfig());
        when(whitelistMapper.selectList(any())).thenReturn(List.of(whitelist("203.0.113.10")));

        try (AnnotationConfigApplicationContext context =
                     context(accessConfigMapper, whitelistMapper, invalidationGuard)) {
            policyCache(context).put("200045", disabledPolicy());

            MerchantOpenApiAccessPolicy policy = context
                    .getBean(MerchantOpenApiAccessPolicyCacheService.class)
                    .findPolicy(" 200045 ");

            assertThat(policy.isWhitelistEnabled()).isTrue();
            assertThat(policy.getAllowedIps()).containsExactly("203.0.113.10");
            verify(accessConfigMapper).selectOne(any());
            verify(whitelistMapper).selectList(any());
        }
    }

    @Test
    void shouldBypassStalePolicyWhenGuardCheckFails() {
        initializeTableMetadata();
        MerchantOpenApiAccessConfigMapper accessConfigMapper = mock(MerchantOpenApiAccessConfigMapper.class);
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(invalidationGuard)
                .isPending(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS, "200045");
        when(accessConfigMapper.selectOne(any())).thenReturn(enabledConfig());
        when(whitelistMapper.selectList(any())).thenReturn(List.of(whitelist("203.0.113.10")));

        try (AnnotationConfigApplicationContext context =
                     context(accessConfigMapper, whitelistMapper, invalidationGuard)) {
            policyCache(context).put("200045", disabledPolicy());

            MerchantOpenApiAccessPolicy policy = context
                    .getBean(MerchantOpenApiAccessPolicyCacheService.class)
                    .findPolicy("200045");

            assertThat(policy.isWhitelistEnabled()).isTrue();
            assertThat(policy.getAllowedIps()).containsExactly("203.0.113.10");
            verify(accessConfigMapper).selectOne(any());
        }
    }

    @Test
    void shouldRejectInsteadOfUsingStalePolicyWhenMasterReadFails() {
        initializeTableMetadata();
        MerchantOpenApiAccessConfigMapper accessConfigMapper = mock(MerchantOpenApiAccessConfigMapper.class);
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(invalidationGuard.isPending(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        )).thenReturn(true);
        when(accessConfigMapper.selectOne(any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        try (AnnotationConfigApplicationContext context =
                     context(accessConfigMapper, whitelistMapper, invalidationGuard)) {
            policyCache(context).put("200045", disabledPolicy());

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
        MerchantOpenApiAccessConfigMapper accessConfigMapper = mock(MerchantOpenApiAccessConfigMapper.class);
        MerchantIpWhitelistMapper whitelistMapper = mock(MerchantIpWhitelistMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(invalidationGuard.isPending(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        )).thenReturn(false, true);
        when(accessConfigMapper.selectOne(any())).thenReturn(enabledConfig());
        when(whitelistMapper.selectList(any())).thenReturn(List.of(whitelist("203.0.113.10")));

        try (AnnotationConfigApplicationContext context =
                     context(accessConfigMapper, whitelistMapper, invalidationGuard)) {
            policyCache(context).put("200045", disabledPolicy());

            MerchantOpenApiAccessPolicy policy = context
                    .getBean(MerchantOpenApiAccessPolicyCacheService.class)
                    .findPolicy("200045");

            assertThat(policy.isWhitelistEnabled()).isTrue();
            assertThat(policy.getAllowedIps()).containsExactly("203.0.113.10");
        }
    }

    @Test
    void shouldRouteCachedAndFreshLoadsToMaster() throws NoSuchMethodException {
        assertMasterRoute("findCached");
        assertMasterRoute("findFresh");
    }

    private AnnotationConfigApplicationContext context(
            MerchantOpenApiAccessConfigMapper accessConfigMapper,
            MerchantIpWhitelistMapper whitelistMapper,
            CacheInvalidationGuard invalidationGuard) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(CachingConfiguration.class);
        context.registerBean(MerchantOpenApiAccessConfigMapper.class, () -> accessConfigMapper);
        context.registerBean(MerchantIpWhitelistMapper.class, () -> whitelistMapper);
        context.registerBean(CacheInvalidationGuard.class, () -> invalidationGuard);
        context.registerBean(MerchantOpenApiAccessPolicyCacheReader.class);
        context.registerBean(MerchantOpenApiAccessPolicyCacheService.class);
        context.refresh();
        return context;
    }

    private void initializeTableMetadata() {
        initializeTableMetadata(MerchantOpenApiAccessConfigDO.class);
        initializeTableMetadata(MerchantIpWhitelistDO.class);
    }

    private void initializeTableMetadata(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private MerchantOpenApiAccessConfigDO enabledConfig() {
        MerchantOpenApiAccessConfigDO config = new MerchantOpenApiAccessConfigDO();
        config.setMerchantId("200045");
        config.setIpWhitelistEnabled(1);
        return config;
    }

    private MerchantIpWhitelistDO whitelist(String ip) {
        MerchantIpWhitelistDO whitelist = new MerchantIpWhitelistDO();
        whitelist.setMerchantId("200045");
        whitelist.setIpValue(ip);
        whitelist.setStatus(1);
        return whitelist;
    }

    private MerchantOpenApiAccessPolicy disabledPolicy() {
        MerchantOpenApiAccessPolicy policy = new MerchantOpenApiAccessPolicy();
        policy.setWhitelistEnabled(false);
        return policy;
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
