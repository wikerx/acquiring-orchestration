package com.scott.payment.component.db.auth.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheMissMarkerStore;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.constant.DataSourceName;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantRuntimeProfileCacheServiceTests
 * @date : 2026-07-30 21:50
 * @email : scott_x@163.com
 * @description : 商户运行时正缓存、独立 miss marker、失效门禁与有界主库回源行为测试
 * @status : create
 */
@Slf4j
class DefaultMerchantRuntimeProfileCacheServiceTests {

    /**
     * 相同商户首次读取查库，后续命中缓存；主动失效后必须重新查库。
     */
    @Test
    void shouldCacheRuntimeProfileAndReloadAfterEviction() {
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(merchantInfoMapper.selectOne(any())).thenReturn(activeMerchant());

        try (AnnotationConfigApplicationContext context =
                     context(merchantInfoMapper, invalidationGuard)) {
            MerchantRuntimeProfileCacheService service =
                    context.getBean(MerchantRuntimeProfileCacheService.class);

            MerchantRuntimeProfile first = service.findRuntimeProfile("200045");
            MerchantRuntimeProfile cached = service.findRuntimeProfile("200045");
            service.evictRuntimeProfile("200045");
            MerchantRuntimeProfile reloaded = service.findRuntimeProfile("200045");

            assertThat(first.getMerchantId()).isEqualTo("200045");
            assertThat(first.getMerchantName()).isEqualTo("Codex Store");
            assertThat(first.getAddressLine()).isEqualTo("1 Market Street");
            assertThat(first.getContactEmail()).isEqualTo("ops@example.com");
            assertThat(first.getGmtCreate()).isEqualTo("2026-07-01T09:00");
            assertThat(cached.getSettlementCurrency()).isEqualTo("USD");
            assertThat(reloaded.getMerchantStatus()).isEqualTo(1);
            verify(merchantInfoMapper, times(2)).selectOne(any());
        }
    }

    /**
     * 门禁 pending 时即使缓存中已有旧状态，也必须绕过缓存并查询主库。
     */
    @Test
    void shouldBypassStaleCacheWhenInvalidationIsPending() {
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(invalidationGuard.isPending(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045"
        )).thenReturn(true);
        when(merchantInfoMapper.selectOne(any())).thenReturn(suspendedMerchant());

        try (AnnotationConfigApplicationContext context =
                     context(merchantInfoMapper, invalidationGuard)) {
            Cache cache = runtimeProfileCache(context);
            cache.put("200045", runtimeProfile(1));

            MerchantRuntimeProfile profile = context
                    .getBean(MerchantRuntimeProfileCacheService.class)
                    .findRuntimeProfile(" 200045 ");

            assertThat(profile.getMerchantStatus()).isZero();
            assertThat(cache.get("200045", MerchantRuntimeProfile.class)
                    .getMerchantStatus()).isEqualTo(1);
            verify(merchantInfoMapper).selectOne(any());
        }
    }

    /**
     * 无法确认门禁状态时按安全路径绕过旧缓存并查询主库。
     */
    @Test
    void shouldBypassStaleCacheWhenGuardCheckFails() {
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(invalidationGuard)
                .isPending(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, "200045");
        when(merchantInfoMapper.selectOne(any())).thenReturn(suspendedMerchant());

        try (AnnotationConfigApplicationContext context =
                     context(merchantInfoMapper, invalidationGuard)) {
            Cache cache = runtimeProfileCache(context);
            cache.put("200045", runtimeProfile(1));

            MerchantRuntimeProfile profile = context
                    .getBean(MerchantRuntimeProfileCacheService.class)
                    .findRuntimeProfile("200045");

            assertThat(profile.getMerchantStatus()).isZero();
            verify(merchantInfoMapper).selectOne(any());
        }
    }

    /**
     * 缓存命中过程中刚建立门禁时，二次检查必须丢弃已读取的旧值。
     */
    @Test
    void shouldReloadWhenGateBecomesPendingDuringCacheRead() {
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        when(invalidationGuard.isPending(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045"
        )).thenReturn(false, true);
        when(merchantInfoMapper.selectOne(any())).thenReturn(suspendedMerchant());

        try (AnnotationConfigApplicationContext context =
                     context(merchantInfoMapper, invalidationGuard)) {
            runtimeProfileCache(context).put("200045", runtimeProfile(1));

            MerchantRuntimeProfile profile = context
                    .getBean(MerchantRuntimeProfileCacheService.class)
                    .findRuntimeProfile("200045");

            assertThat(profile.getMerchantStatus()).isZero();
            verify(merchantInfoMapper).selectOne(any());
        }
    }

    /**
     * 缓存未命中和安全绕过入口都必须显式路由到主库。
     */
    @Test
    void shouldRouteNormalLoadsToSlaveAndForcedRefreshToMaster() throws NoSuchMethodException {
        assertDataSourceRoute("findCached", DataSourceName.SLAVE);
        assertDataSourceRoute("findFresh", DataSourceName.MASTER);
        assertDataSourceRoute("refresh", DataSourceName.MASTER);
    }

    /**
     * IP 白名单或开关变化时必须删除聚合后的 OpenAPI 访问策略缓存。
     */
    @Test
    void shouldEvictOpenApiAccessPolicy() {
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);

        try (AnnotationConfigApplicationContext context =
                     context(merchantInfoMapper, invalidationGuard)) {
            Cache cache = context.getBean(CacheManager.class)
                    .getCache(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS);
            assertThat(cache).isNotNull();
            cache.put("200045", "cached-policy");

            context.getBean(MerchantRuntimeProfileCacheService.class)
                    .evictOpenApiAccessPolicy("200045");

            assertThat(cache.get("200045")).isNull();
        }
    }

    /**
     * 数据库明确返回不存在后必须写入短 TTL marker，后续请求由 marker 阻止重复回源。
     */
    @Test
    void shouldMarkConfirmedDatabaseMissAndAvoidRepeatedFallback() {
        log.info("测试商户资料负缓存，关键输入: 主库明确返回空记录、第二次读取 marker 命中");
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        CacheMissMarkerStore missMarkerStore = mock(CacheMissMarkerStore.class);
        when(missMarkerStore.lookup("merchant", "runtime-profile-miss", "404001"))
                .thenReturn(
                        CacheMissMarkerStore.LookupStatus.ABSENT,
                        CacheMissMarkerStore.LookupStatus.PRESENT
                );
        when(merchantInfoMapper.selectOne(any())).thenReturn(null);

        try (AnnotationConfigApplicationContext context =
                     context(merchantInfoMapper, invalidationGuard, missMarkerStore)) {
            MerchantRuntimeProfileCacheService service =
                    context.getBean(MerchantRuntimeProfileCacheService.class);

            assertThat(service.findRuntimeProfile("404001")).isNull();
            assertThat(service.findRuntimeProfile("404001")).isNull();

            verify(merchantInfoMapper, times(1)).selectOne(any());
            verify(missMarkerStore).markMissing(
                    eq("merchant"),
                    eq("runtime-profile-miss"),
                    eq("404001"),
                    eq(Duration.ofSeconds(30)),
                    eq(10)
            );
        }
        log.info("商户资料负缓存验证完成，结果: 两次请求仅回源主库 1 次");
    }

    /**
     * Redis miss marker 读取失败时必须继续查询主库，并禁止把故障状态写成 marker。
     */
    @Test
    void shouldNotCreateMissMarkerWhenMarkerLookupIsUnavailable() {
        log.info("测试 Redis 故障不生成负缓存，关键输入: marker 查询状态 UNAVAILABLE");
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        CacheMissMarkerStore missMarkerStore = mock(CacheMissMarkerStore.class);
        when(missMarkerStore.lookup("merchant", "runtime-profile-miss", "404002"))
                .thenReturn(CacheMissMarkerStore.LookupStatus.UNAVAILABLE);
        when(merchantInfoMapper.selectOne(any())).thenReturn(null);

        try (AnnotationConfigApplicationContext context =
                     context(merchantInfoMapper, invalidationGuard, missMarkerStore)) {
            MerchantRuntimeProfileCacheService service =
                    context.getBean(MerchantRuntimeProfileCacheService.class);

            assertThat(service.findRuntimeProfile("404002")).isNull();
            assertThat(service.findRuntimeProfile("404002")).isNull();

            verify(merchantInfoMapper, times(2)).selectOne(any());
            verify(missMarkerStore, never()).markMissing(
                    any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        }
        log.info("Redis 故障不生成负缓存验证完成，结果: 两次请求均回源且未写 marker");
    }

    /**
     * 进程内主动失效必须删除独立 miss marker，防止新增商户继续命中旧的不存在结论。
     */
    @Test
    void shouldEvictMissMarkerWithRuntimeProfileCache() {
        log.info("测试商户资料正负缓存协同失效，关键输入: 商户号 200045");
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        CacheMissMarkerStore missMarkerStore = mock(CacheMissMarkerStore.class);

        try (AnnotationConfigApplicationContext context =
                     context(merchantInfoMapper, invalidationGuard, missMarkerStore)) {
            context.getBean(MerchantRuntimeProfileCacheService.class)
                    .evictRuntimeProfile(" 200045 ");

            verify(missMarkerStore).evict(
                    "merchant", "runtime-profile-miss", "200045");
        }
        log.info("商户资料正负缓存协同失效验证完成，结果: miss marker 已精确删除");
    }

    /**
     * 主库回源达到并发上限时必须返回 F503，且首个查询结束后许可必须恢复。
     *
     * @throws Exception 并发测试等待被中断或异步查询失败
     */
    @Test
    void shouldRejectFallbackBeyondConcurrencyLimitAndReleasePermit() throws Exception {
        log.info("测试商户资料回源并发保护，关键输入: 并发上限 1、首个主库查询阻塞");
        initializeTableMetadata(BaseMerchantInfoDO.class);
        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        CountDownLatch queryStarted = new CountDownLatch(1);
        CountDownLatch allowQueryToFinish = new CountDownLatch(1);
        when(merchantInfoMapper.selectOne(any())).thenAnswer(invocation -> {
            queryStarted.countDown();
            if (!allowQueryToFinish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test database query wait timed out");
            }
            return activeMerchant();
        });
        MerchantRuntimeProfileCacheReader reader =
                new MerchantRuntimeProfileCacheReader(merchantInfoMapper, 1);
        CompletableFuture<MerchantRuntimeProfile> firstLoad = CompletableFuture.supplyAsync(
                () -> reader.findFresh("200045")
        );

        try {
            assertThat(queryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> reader.findFresh("200046"))
                    .isInstanceOf(ServiceException.class)
                    .satisfies(exception -> assertThat(((ServiceException) exception).getCode())
                            .isEqualTo(ApiResultEnum.NETWORK_BUSY.getCode()));
        } finally {
            allowQueryToFinish.countDown();
        }

        assertThat(firstLoad.get(5, TimeUnit.SECONDS).getMerchantId()).isEqualTo("200045");
        log.info("商户资料回源并发保护验证完成，结果: 超限请求返回 F503 且许可已释放");
    }

    /**
     * 创建不启用 miss marker 的缓存测试上下文。
     *
     * @param merchantInfoMapper 商户 Mapper
     * @param invalidationGuard  失效门禁
     * @return 已刷新 Spring 测试上下文
     */
    private AnnotationConfigApplicationContext context(
            BaseMerchantInfoMapper merchantInfoMapper,
            CacheInvalidationGuard invalidationGuard) {
        return context(merchantInfoMapper, invalidationGuard, null);
    }

    /**
     * 创建可指定 miss marker 存储的缓存测试上下文。
     *
     * @param merchantInfoMapper 商户 Mapper
     * @param invalidationGuard  失效门禁
     * @param missMarkerStore    miss marker 存储；允许为空
     * @return 已刷新 Spring 测试上下文
     */
    private AnnotationConfigApplicationContext context(
            BaseMerchantInfoMapper merchantInfoMapper,
            CacheInvalidationGuard invalidationGuard,
            CacheMissMarkerStore missMarkerStore) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(CachingConfiguration.class);
        context.registerBean(BaseMerchantInfoMapper.class, () -> merchantInfoMapper);
        context.registerBean(CacheInvalidationGuard.class, () -> invalidationGuard);
        if (missMarkerStore != null) {
            context.registerBean(CacheMissMarkerStore.class, () -> missMarkerStore);
        }
        context.registerBean(MerchantRuntimeProfileCacheReader.class);
        context.registerBean(DefaultMerchantRuntimeProfileCacheService.class);
        context.refresh();
        return context;
    }

    private void initializeTableMetadata(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    private BaseMerchantInfoDO activeMerchant() {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setId(2081299574373662721L);
        merchant.setMerchantId("200045");
        merchant.setMerchantName("Codex Store");
        merchant.setBillingDescriptor("CODEX STORE");
        merchant.setMerchantShortName("Codex");
        merchant.setMerchantStatus(1);
        merchant.setMerchantCategoryCode("5311");
        merchant.setCountryCode("USA");
        merchant.setRegionCode("CA");
        merchant.setCity("San Francisco");
        merchant.setAddressLine("1 Market Street");
        merchant.setPostalCode("94105");
        merchant.setContactName("Operations");
        merchant.setContactEmail("ops@example.com");
        merchant.setContactPhone("+1-555-0100");
        merchant.setSettlementCurrency("USD");
        merchant.setTimezone("Asia/Shanghai");
        merchant.setRiskLevel(2);
        merchant.setGmtCreate(java.time.LocalDateTime.of(2026, 7, 1, 9, 0));
        merchant.setGmtModified(java.time.LocalDateTime.of(2026, 8, 1, 12, 0));
        return merchant;
    }

    private BaseMerchantInfoDO suspendedMerchant() {
        BaseMerchantInfoDO merchant = activeMerchant();
        merchant.setMerchantStatus(0);
        return merchant;
    }

    private MerchantRuntimeProfile runtimeProfile(int status) {
        MerchantRuntimeProfile profile = new MerchantRuntimeProfile();
        profile.setMerchantId("200045");
        profile.setMerchantStatus(status);
        profile.setSettlementCurrency("USD");
        return profile;
    }

    private Cache runtimeProfileCache(AnnotationConfigApplicationContext context) {
        Cache cache = context.getBean(CacheManager.class)
                .getCache(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE);
        assertThat(cache).isNotNull();
        return cache;
    }

    private void assertDataSourceRoute(String methodName, String expectedDataSource) throws NoSuchMethodException {
        DS dataSource = MerchantRuntimeProfileCacheReader.class
                .getMethod(methodName, String.class)
                .getAnnotation(DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(expectedDataSource);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CachingConfiguration {

        @org.springframework.context.annotation.Bean
        CacheManager cacheManager() {
            ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                    PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                    PaymentCacheNames.MERCHANT_OPENAPI_ACCESS
            );
            cacheManager.setAllowNullValues(false);
            return cacheManager;
        }
    }
}
