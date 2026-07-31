package com.scott.payment.payment.schedule;

import com.scott.payment.payment.service.TransactionEventOutboxRelayService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 交易生命周期 Outbox 调度测试，验证单批上限和调度器只委托 relay 服务处理待发送事件。
 */
class TransactionEventOutboxRelaySchedulerTests {

    @Test
    void shouldSelectConfiguredProductionConstructorWhenCreatedBySpring() {
        TransactionEventOutboxRelayService relayService =
                mock(TransactionEventOutboxRelayService.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "outbox-scheduler-test",
                    Map.of(
                            "payment.transaction.outbox.relay-enabled", "true",
                            "payment.transaction.outbox.batch-size", "50",
                            "payment.transaction.outbox.lookback-quarters", "3"
                    )
            ));
            context.registerBean(TransactionEventOutboxRelayService.class, () -> relayService);
            context.register(TransactionEventOutboxRelayScheduler.class);

            context.refresh();

            assertThat(context.getBean(TransactionEventOutboxRelayScheduler.class)).isNotNull();
        }
    }

    @Test
    void shouldRelayCurrentAndConfiguredHistoricalQuarterTables() {
        TransactionEventOutboxRelayService relayService =
                mock(TransactionEventOutboxRelayService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-30T02:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        TransactionEventOutboxRelayScheduler scheduler =
                new TransactionEventOutboxRelayScheduler(relayService, 50, 3, clock);

        scheduler.relay();

        verify(relayService).publishDueEvents(
                LocalDateTime.of(2026, 7, 30, 10, 0), 50);
        verify(relayService).publishDueEvents(
                LocalDateTime.of(2026, 4, 30, 10, 0), 50);
        verify(relayService).publishDueEvents(
                LocalDateTime.of(2026, 1, 30, 10, 0), 50);
    }
}
