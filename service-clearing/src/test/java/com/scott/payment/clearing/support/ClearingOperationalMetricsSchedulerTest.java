package com.scott.payment.clearing.support;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 验证一次指标刷新失败不会逃逸并终止后续 Spring 调度。 */
class ClearingOperationalMetricsSchedulerTest {

    @Test
    void shouldContainRefreshFailureForNextSchedule() {
        ClearingOperationalMetricsRefreshService refreshService =
                mock(ClearingOperationalMetricsRefreshService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("replica unavailable"))
                .when(refreshService).refresh();
        ClearingOperationalMetricsScheduler scheduler =
                new ClearingOperationalMetricsScheduler(refreshService);

        scheduler.refresh();

        verify(refreshService).refresh();
    }
}
