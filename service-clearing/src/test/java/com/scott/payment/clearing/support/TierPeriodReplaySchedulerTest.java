package com.scott.payment.clearing.support;

import com.scott.payment.clearing.service.TierPeriodReplayService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 验证阶梯重放随服务自动调度，单次扫描异常不会终止后续调度周期。 */
class TierPeriodReplaySchedulerTest {

    @Test
    void schedulerShouldUseFixedBuiltInCadenceWithoutConfigurationSwitch() throws Exception {
        Scheduled scheduled = TierPeriodReplayScheduler.class.getDeclaredMethod("run")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.initialDelay()).isEqualTo(5000L);
        assertThat(scheduled.fixedDelay()).isEqualTo(5000L);
        assertThat(scheduled.initialDelayString()).isEmpty();
        assertThat(scheduled.fixedDelayString()).isEmpty();
    }

    @Test
    void runShouldUseUtcClockAndContainScanLevelFailure() {
        TierPeriodReplayService replayService = mock(TierPeriodReplayService.class);
        Instant now = Instant.parse("2026-08-26T10:30:00Z");
        doThrow(new IllegalStateException("database temporarily unavailable"))
                .when(replayService).runDue(20, now);
        TierPeriodReplayScheduler scheduler = new TierPeriodReplayScheduler(
                replayService, Clock.fixed(now, ZoneOffset.UTC));

        assertThatCode(scheduler::run).doesNotThrowAnyException();
        verify(replayService).runDue(20, now);
    }
}
