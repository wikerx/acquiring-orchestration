package com.scott.payment.admin.application.risk.cache;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 风控缓存失效重试调度测试。
 */
class RiskCacheInvalidationRetrySchedulerTests {

    @Test
    void shouldPublishBoundedRetryBatchOnEverySchedule() throws NoSuchMethodException {
        RiskCacheInvalidationRelayService relayService =
                mock(RiskCacheInvalidationRelayService.class);
        RiskCacheInvalidationRetryScheduler scheduler =
                new RiskCacheInvalidationRetryScheduler(relayService);

        scheduler.retryDueEvents();

        verify(relayService).publishDueEvents(100);
        Scheduled scheduled = RiskCacheInvalidationRetryScheduler.class
                .getMethod("retryDueEvents")
                .getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .contains("relay-fixed-delay-ms:5000");
    }
}
