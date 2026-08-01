package com.scott.payment.component.redis.cache.invalidation;

import com.scott.payment.component.core.cache.CacheMissMarkerStore;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ImmediateCacheEvictionServiceTests
 * @date : 2026-07-30 21:40
 * @email : scott_x@163.com
 * @description : 安全缓存立即失效与商户资料正负缓存协同删除测试
 * @status : create
 */
@Slf4j
class ImmediateCacheEvictionServiceTests {

    /**
     * Spring Cache 关闭时不得注册立即失效服务，避免降级启动因缺少 CacheManager 失败。
     */
    @Test
    void shouldNotRegisterWhenCacheManagerIsUnavailable() {
        log.info("测试 Spring Cache 关闭降级，关键输入: 容器中不存在 CacheManager");
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ImmediateCacheEvictionService.class);
            context.refresh();

            assertThat(context.getBeansOfType(ImmediateCacheEvictionService.class)).isEmpty();
        }
        log.info("Spring Cache 关闭降级验证完成，结果: 立即失效服务未注册且容器正常启动");
    }

    /**
     * 事务感知装饰器必须被绕过，商户资料正缓存和 miss marker 必须在同一次调用中删除。
     */
    @Test
    void shouldBypassTransactionAwareDecoratorAndEvictImmediately() {
        log.info("测试商户资料立即失效，关键输入: 事务感知正缓存和独立 miss marker");
        CacheManager cacheManager = mock(CacheManager.class);
        Cache targetCache = mock(Cache.class);
        CacheMissMarkerStore missMarkerStore = mock(CacheMissMarkerStore.class);
        TransactionAwareCacheDecorator decorator = new TransactionAwareCacheDecorator(targetCache);
        when(cacheManager.getCache(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE))
                .thenReturn(decorator);
        ImmediateCacheEvictionService service = new ImmediateCacheEvictionService(
                cacheManager,
                provider(missMarkerStore)
        );

        service.evict(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, "200045");

        verify(targetCache).evict("200045");
        verify(missMarkerStore).evict("merchant", "runtime-profile-miss", "200045");
        log.info("商户资料立即失效验证完成，结果: 正缓存与 miss marker 均已删除");
    }

    /**
     * 未登记 Cache Name 必须在访问 CacheManager 前被拒绝。
     */
    @Test
    void shouldRejectUnregisteredCacheName() {
        log.info("测试未登记 Cache Name 拒绝，关键输入: unregistered:cache");
        ImmediateCacheEvictionService service =
                new ImmediateCacheEvictionService(
                        mock(CacheManager.class),
                        provider(null)
                );

        assertThatIllegalArgumentException().isThrownBy(() ->
                service.evict("unregistered:cache", "200045"));
        log.info("未登记 Cache Name 拒绝验证完成，结果: IllegalArgumentException");
    }

    /**
     * 创建返回指定 miss marker 存储的 ObjectProvider。
     *
     * @param missMarkerStore marker 存储；允许为空
     * @return ObjectProvider 测试替身
     */
    @SuppressWarnings("unchecked")
    private ObjectProvider<CacheMissMarkerStore> provider(CacheMissMarkerStore missMarkerStore) {
        ObjectProvider<CacheMissMarkerStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(missMarkerStore);
        return provider;
    }
}
