package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxRelaySchedulerTests
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 验证通用 Outbox 到期投递、超时恢复的职责隔离及数据库扫描开关
 * @status : create
 */
class ReliableMqOutboxRelaySchedulerTests {

    /** 到期扫描只能查询和投递到期事件，不得顺带执行超时恢复 UPDATE。 */
    @Test
    void shouldRelayDueEventsWithoutRecoveringStaleEvents() {
        ReliableMqOutboxRelayService relayService = mock(ReliableMqOutboxRelayService.class);
        ReliableMqOutboxRelayScheduler scheduler = scheduler(relayService, true, true);

        scheduler.relayDue();

        verify(relayService).relayDue();
        verify(relayService, never()).recoverStale();
    }

    /** 超时恢复任务只能处理 PROCESSING 消息，不得重复执行到期消息 SELECT。 */
    @Test
    void shouldRecoverStaleEventsWithoutPollingDueEvents() {
        ReliableMqOutboxRelayService relayService = mock(ReliableMqOutboxRelayService.class);
        ReliableMqOutboxRelayScheduler scheduler = scheduler(relayService, true, true);

        scheduler.recoverStale();

        verify(relayService).recoverStale();
        verify(relayService, never()).relayDue();
    }

    /** 全局或服务职责开关关闭时，所有补偿任务均不得访问数据库。 */
    @Test
    void shouldSkipAllDatabaseScansWhenServiceRelayIsDisabled() {
        ReliableMqOutboxRelayService relayService = mock(ReliableMqOutboxRelayService.class);
        ReliableMqOutboxRelayScheduler scheduler = scheduler(relayService, true, false);

        scheduler.relayDue();
        scheduler.recoverStale();
        scheduler.refreshMetrics();

        verifyNoInteractions(relayService);
    }

    /** 调度配置必须分别暴露到期扫描和超时恢复周期，避免二者再次绑定为同一个高频任务。 */
    @Test
    void shouldUseIndependentConfigurableSchedules() throws NoSuchMethodException {
        Method relayDue = ReliableMqOutboxRelayScheduler.class.getMethod("relayDue");
        Method recoverStale = ReliableMqOutboxRelayScheduler.class.getMethod("recoverStale");

        Scheduled relaySchedule = relayDue.getAnnotation(Scheduled.class);
        Scheduled recoverySchedule = recoverStale.getAnnotation(Scheduled.class);

        assertThat(relaySchedule.initialDelayString())
                .isEqualTo("${acquiring.mq.outbox.relay-initial-delay-ms:5000}");
        assertThat(relaySchedule.fixedDelayString())
                .isEqualTo("${acquiring.mq.outbox.relay-delay-ms:5000}");
        assertThat(recoverySchedule.initialDelayString())
                .isEqualTo("${acquiring.mq.outbox.recovery-initial-delay-ms:30000}");
        assertThat(recoverySchedule.fixedDelayString())
                .isEqualTo("${acquiring.mq.outbox.recovery-delay-ms:30000}");
    }

    private ReliableMqOutboxRelayScheduler scheduler(ReliableMqOutboxRelayService relayService,
                                                      boolean relayEnabled,
                                                      boolean serviceRelayEnabled) {
        ReliableMqOutboxProperties properties = new ReliableMqOutboxProperties();
        properties.setRelayEnabled(relayEnabled);
        properties.setServiceRelayEnabled(serviceRelayEnabled);
        return new ReliableMqOutboxRelayScheduler(relayService, properties);
    }
}
