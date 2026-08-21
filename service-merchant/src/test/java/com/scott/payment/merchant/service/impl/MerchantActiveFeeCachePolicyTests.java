package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantActiveFeeCachePolicyTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 商户生效费率缓存门禁读取策略测试
 * @status : create
 */
class MerchantActiveFeeCachePolicyTests {

    /** 门禁明确空闲时允许读取缓存。 */
    @Test
    void shouldAllowCacheReadWhenNoInvalidationIsPending() {
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        when(guard.isPending(PaymentCacheNames.MERCHANT_ACTIVE_FEE, "200045"))
                .thenReturn(false);

        assertThat(new MerchantActiveFeeCachePolicy(guard).isCacheReadAllowed("200045"))
                .isTrue();
    }

    /** 门禁进行中或 Redis 状态未知时必须绕过永久缓存。 */
    @Test
    void shouldBypassCacheForPendingOrUnknownGateState() {
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        when(guard.isPending(PaymentCacheNames.MERCHANT_ACTIVE_FEE, "200045"))
                .thenReturn(true);
        when(guard.isPending(PaymentCacheNames.MERCHANT_ACTIVE_FEE, "200046"))
                .thenThrow(new IllegalStateException("redis unavailable"));
        MerchantActiveFeeCachePolicy policy = new MerchantActiveFeeCachePolicy(guard);

        assertThat(policy.isCacheReadAllowed("200045")).isFalse();
        assertThat(policy.isCacheReadAllowed("200046")).isFalse();
    }
}
