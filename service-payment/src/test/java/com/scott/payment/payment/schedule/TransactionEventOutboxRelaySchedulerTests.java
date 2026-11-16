package com.scott.payment.payment.schedule;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.service.TransactionEventOutboxRelayService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 交易生命周期 Outbox 调度测试，验证有界连续排空配置和已发布季度扫描范围。
 */
class TransactionEventOutboxRelaySchedulerTests {

    @Test
    void shouldSelectConfiguredProductionConstructorWhenCreatedBySpring() {
        TransactionEventOutboxRelayService relayService =
                mock(TransactionEventOutboxRelayService.class);
        TransactionShardingProperties shardingProperties = verifiedNodes("202603", "202602");
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "outbox-scheduler-test",
                    Map.of(
                            "payment.transaction.outbox.relay-enabled", "true",
                            "payment.transaction.outbox.batch-size", "50",
                            "payment.transaction.outbox.max-batches-per-run", "3"
                    )
            ));
            context.registerBean(TransactionEventOutboxRelayService.class, () -> relayService);
            context.registerBean(TransactionShardingProperties.class, () -> shardingProperties);
            context.register(TransactionEventOutboxRelayScheduler.class);

            context.refresh();

            TransactionEventOutboxRelayScheduler scheduler =
                    context.getBean(TransactionEventOutboxRelayScheduler.class);
            assertThat(ReflectionTestUtils.getField(scheduler, "batchSize")).isEqualTo(50);
            assertThat(ReflectionTestUtils.getField(scheduler, "maxBatchesPerRun")).isEqualTo(3);
        }
    }

    @Test
    void shouldRelayOnlyCurrentAndPreviousPublishedQuarterFrequently() {
        TransactionEventOutboxRelayService relayService =
                mock(TransactionEventOutboxRelayService.class);
        TransactionShardingProperties shardingProperties = verifiedNodes(
                "202501", "202502", "202503", "202504", "202601", "202602", "202603", "202604");
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-30T02:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        TransactionEventOutboxRelayScheduler scheduler =
                new TransactionEventOutboxRelayScheduler(relayService, shardingProperties, 50, clock);

        scheduler.relayRecent();

        verify(relayService).publishDueEvents(
                LocalDateTime.of(2026, 7, 1, 0, 0), 50, 5);
        verify(relayService).publishDueEvents(
                LocalDateTime.of(2026, 4, 1, 0, 0), 50, 5);
        verify(relayService, org.mockito.Mockito.never()).publishDueEvents(
                LocalDateTime.of(2026, 1, 1, 0, 0), 50, 5);
        verify(relayService, org.mockito.Mockito.never()).publishDueEvents(
                LocalDateTime.of(2026, 10, 1, 0, 0), 50, 5);
    }

    @Test
    void shouldRelayAllOlderPublishedQuartersAtHistoricalFrequency() {
        TransactionEventOutboxRelayService relayService = mock(TransactionEventOutboxRelayService.class);
        TransactionShardingProperties shardingProperties = verifiedNodes(
                "202501", "202502", "202503", "202504", "202601", "202602", "202603", "202604");
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-30T02:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        TransactionEventOutboxRelayScheduler scheduler =
                new TransactionEventOutboxRelayScheduler(relayService, shardingProperties, 50, clock);

        scheduler.relayHistorical();

        verify(relayService).publishDueEvents(LocalDateTime.of(2026, 1, 1, 0, 0), 50, 5);
        verify(relayService).publishDueEvents(LocalDateTime.of(2025, 10, 1, 0, 0), 50, 5);
        verify(relayService).publishDueEvents(LocalDateTime.of(2025, 7, 1, 0, 0), 50, 5);
        verify(relayService).publishDueEvents(LocalDateTime.of(2025, 4, 1, 0, 0), 50, 5);
        verify(relayService).publishDueEvents(LocalDateTime.of(2025, 1, 1, 0, 0), 50, 5);
        verify(relayService, org.mockito.Mockito.never()).publishDueEvents(
                LocalDateTime.of(2026, 7, 1, 0, 0), 50, 5);
        verify(relayService, org.mockito.Mockito.never()).publishDueEvents(
                LocalDateTime.of(2026, 4, 1, 0, 0), 50, 5);
    }

    @Test
    void shouldBoundBatchSizeAndBatchBudgetBeforeDelegating() {
        TransactionEventOutboxRelayService relayService = mock(TransactionEventOutboxRelayService.class);
        TransactionShardingProperties shardingProperties = verifiedNodes("202603");
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-30T02:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        TransactionEventOutboxRelayScheduler scheduler =
                new TransactionEventOutboxRelayScheduler(
                        relayService, shardingProperties, 5_000, 200, 2, clock);

        scheduler.relayRecent();

        verify(relayService).publishDueEvents(
                LocalDateTime.of(2026, 7, 1, 0, 0), 500, 20);
    }

    @Test
    void shouldRefreshMetricsAcrossEveryPublishedQuarterNotAfterCurrentQuarter() {
        TransactionEventOutboxRelayService relayService = mock(TransactionEventOutboxRelayService.class);
        TransactionShardingProperties shardingProperties = verifiedNodes("202601", "202602", "202603", "202604");
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-30T02:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        TransactionEventOutboxRelayScheduler scheduler =
                new TransactionEventOutboxRelayScheduler(relayService, shardingProperties, 50, clock);

        scheduler.refreshMetrics();

        verify(relayService).refreshMetrics(java.util.List.of(
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0)));
    }

    /**
     * Outbox 只从配置起始季度开始存在，调度回看不得访问更早且未创建的物理表。
     */
    @Test
    void shouldRejectMalformedPublishedNodeInsteadOfSilentlySkippingIt() {
        TransactionEventOutboxRelayService relayService = mock(TransactionEventOutboxRelayService.class);
        TransactionShardingProperties shardingProperties = verifiedNodes("202603", "202605");
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-30T02:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        TransactionEventOutboxRelayScheduler scheduler =
                new TransactionEventOutboxRelayScheduler(relayService, shardingProperties, 50, clock);

        org.assertj.core.api.Assertions.assertThatThrownBy(scheduler::relayRecent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yyyyQQ");
    }

    private TransactionShardingProperties verifiedNodes(String... nodes) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(java.util.List.of(nodes));
        return properties;
    }
}
