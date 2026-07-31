package com.scott.payment.admin.application.cache;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 商户安全缓存失效重试调度测试。
 */
class MerchantSecurityCacheInvalidationRetrySchedulerTests {

    @Test
    void shouldPublishBoundedRetryBatchOnEverySchedule() throws NoSuchMethodException {
        MerchantSecurityCacheInvalidationRelayService relay =
                mock(MerchantSecurityCacheInvalidationRelayService.class);
        MerchantSecurityCacheInvalidationRetryScheduler scheduler =
                new MerchantSecurityCacheInvalidationRetryScheduler(relay);

        scheduler.retryDueEvents();

        verify(relay).publishDueEvents(100);
        Scheduled scheduled = MerchantSecurityCacheInvalidationRetryScheduler.class
                .getMethod("retryDueEvents")
                .getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .contains("relay-fixed-delay-ms:5000");
    }
}
