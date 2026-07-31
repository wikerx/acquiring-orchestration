package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台公开配置永久缓存读路径测试。
 */
@Slf4j
class MerchantConfigServiceImplTests {

    /**
     * 验证读取开始前已存在 pending 门禁时完全绕过 Spring Cache。
     */
    @Test
    void shouldReadMasterDirectlyWhenInvalidationIsAlreadyPending() {
        log.info("测试平台配置 pending 前置检查，关键输入: platform.gateway.base-url 已进入失效流程");
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        PlatformConfigCacheReader reader = mock(PlatformConfigCacheReader.class);
        when(guard.isPending(
                PaymentCacheNames.PLATFORM_CONFIG,
                "platform.gateway.base-url"
        )).thenReturn(true);
        when(reader.findFresh("platform.gateway.base-url"))
                .thenReturn(Optional.of("https://gateway.example.com"));
        MerchantConfigServiceImpl service = new MerchantConfigServiceImpl(guard, reader);

        Optional<String> result = service.enabledConfigValue(" platform.gateway.base-url ");

        assertThat(result).contains("https://gateway.example.com");
        verify(reader, never()).findCached("platform.gateway.base-url");
        verify(reader).findFresh("platform.gateway.base-url");
        log.info("平台配置 pending 前置检查测试完成，结果: 未读取永久缓存并直接返回主库值");
    }

    /**
     * 验证缓存读取期间新出现 pending 门禁时丢弃刚取得的缓存值并重新读取主库。
     */
    @Test
    void shouldDiscardCachedValueWhenInvalidationStartsDuringRead() {
        log.info("测试平台配置 pending 后置检查，关键输入: 缓存读取前 false、读取后 true");
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        PlatformConfigCacheReader reader = mock(PlatformConfigCacheReader.class);
        when(guard.isPending(
                PaymentCacheNames.PLATFORM_CONFIG,
                "platform.checkout.frontend-base-url"
        )).thenReturn(false, true);
        when(reader.findCached("platform.checkout.frontend-base-url"))
                .thenReturn(Optional.of("https://old-checkout.example.com"));
        when(reader.findFresh("platform.checkout.frontend-base-url"))
                .thenReturn(Optional.of("https://checkout.example.com"));
        MerchantConfigServiceImpl service = new MerchantConfigServiceImpl(guard, reader);

        Optional<String> result =
                service.enabledConfigValue("platform.checkout.frontend-base-url");

        assertThat(result).contains("https://checkout.example.com");
        var ordered = inOrder(guard, reader);
        ordered.verify(guard).isPending(
                PaymentCacheNames.PLATFORM_CONFIG,
                "platform.checkout.frontend-base-url"
        );
        ordered.verify(reader).findCached("platform.checkout.frontend-base-url");
        ordered.verify(guard).isPending(
                PaymentCacheNames.PLATFORM_CONFIG,
                "platform.checkout.frontend-base-url"
        );
        ordered.verify(reader).findFresh("platform.checkout.frontend-base-url");
        log.info("平台配置 pending 后置检查测试完成，结果: 旧缓存值被丢弃并返回主库新值");
    }

    /**
     * 验证 Redis 门禁状态查询异常时按普通查询缓存故障策略降级主库。
     */
    @Test
    void shouldReadMasterWhenGateStateCannotBeDetermined() {
        log.info("测试平台配置门禁异常降级，关键输入: Redis 连接异常");
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        PlatformConfigCacheReader reader = mock(PlatformConfigCacheReader.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(guard)
                .isPending(
                        PaymentCacheNames.PLATFORM_CONFIG,
                        "platform.merchant.frontend-base-url"
                );
        when(reader.findFresh("platform.merchant.frontend-base-url"))
                .thenReturn(Optional.of("https://merchant.example.com"));
        MerchantConfigServiceImpl service = new MerchantConfigServiceImpl(guard, reader);

        Optional<String> result =
                service.enabledConfigValue("platform.merchant.frontend-base-url");

        assertThat(result).contains("https://merchant.example.com");
        verify(reader, never()).findCached("platform.merchant.frontend-base-url");
        log.info("平台配置门禁异常降级测试完成，结果: 未使用状态未知的缓存并返回主库值");
    }

    /**
     * 验证未登记或敏感配置不会访问 Redis，也不会通过该公开配置服务查询数据库。
     */
    @Test
    void shouldRejectUnregisteredConfigBeforeAccessingCacheInfrastructure() {
        log.info("测试平台配置缓存白名单，关键输入: platform.gateway.api-secret");
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        PlatformConfigCacheReader reader = mock(PlatformConfigCacheReader.class);
        MerchantConfigServiceImpl service = new MerchantConfigServiceImpl(guard, reader);

        Optional<String> result = service.enabledConfigValue("platform.gateway.api-secret");

        assertThat(result).isEmpty();
        verify(guard, never()).isPending(
                PaymentCacheNames.PLATFORM_CONFIG,
                "platform.gateway.api-secret"
        );
        verify(reader, never()).findCached("platform.gateway.api-secret");
        verify(reader, never()).findFresh("platform.gateway.api-secret");
        log.info("平台配置缓存白名单测试完成，结果: 敏感或未登记配置未访问缓存基础设施");
    }
}
