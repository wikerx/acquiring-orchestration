package com.scott.payment.admin.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证节假日月视图缓存键和失效门禁的安全读取契约。 */
class HolidayCalendarCachePolicyTests {

    /** 单位数月份必须补零，避免同一月份生成多个缓存键。 */
    @Test
    void shouldNormalizeMonthKeyAndAllowReadWhenNoInvalidationIsPending() {
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        when(guard.isPending(PaymentCacheNames.SETTLEMENT_HOLIDAY_MONTH, "2026-08"))
                .thenReturn(false);
        HolidayCalendarCachePolicy policy = new HolidayCalendarCachePolicy(guard);

        assertThat(policy.monthKey(2026, 8)).isEqualTo("2026-08");
        assertThat(policy.isCacheReadAllowed(2026, 8)).isTrue();
        verify(guard).isPending(PaymentCacheNames.SETTLEMENT_HOLIDAY_MONTH, "2026-08");
    }

    /** Redis 门禁状态无法确定时必须绕过永久缓存。 */
    @Test
    void shouldBypassCacheWhenGuardLookupFails() {
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        when(guard.isPending(PaymentCacheNames.SETTLEMENT_HOLIDAY_MONTH, "2026-08"))
                .thenThrow(new IllegalStateException("redis unavailable"));
        HolidayCalendarCachePolicy policy = new HolidayCalendarCachePolicy(guard);

        assertThat(policy.isCacheReadAllowed(2026, 8)).isFalse();
    }
}
