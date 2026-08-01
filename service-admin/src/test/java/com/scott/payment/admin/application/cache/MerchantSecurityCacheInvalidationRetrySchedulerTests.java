package com.scott.payment.admin.application.cache;

import com.scott.payment.admin.observability.CacheInvalidationOutboxMetrics;
import com.scott.payment.component.db.cache.model.CacheInvalidationBatchResult;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationRelayService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户安全缓存失效重试调度测试。
 */
class MerchantSecurityCacheInvalidationRetrySchedulerTests {

    @Test
    void shouldPublishBoundedRetryBatchOnEverySchedule() throws NoSuchMethodException {
        ManagedCacheInvalidationRelayService relay =
                mock(ManagedCacheInvalidationRelayService.class);
        CacheInvalidationOutboxMetrics metrics = mock(CacheInvalidationOutboxMetrics.class);
        when(relay.publishDueEvents(100)).thenReturn(new CacheInvalidationBatchResult(3, 2));
        MerchantSecurityCacheInvalidationRetryScheduler scheduler =
                new MerchantSecurityCacheInvalidationRetryScheduler(relay, metrics);

        scheduler.retryDueEvents();

        verify(relay).publishDueEvents(100);
        verify(metrics).recordBatch(
                org.mockito.ArgumentMatchers.eq(CacheInvalidationOutboxMetrics.Outbox.MERCHANT_SECURITY),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq(100),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.anyLong()
        );
        Scheduled scheduled = MerchantSecurityCacheInvalidationRetryScheduler.class
                .getMethod("retryDueEvents")
                .getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .contains("relay-fixed-delay-ms:5000");
    }
}
