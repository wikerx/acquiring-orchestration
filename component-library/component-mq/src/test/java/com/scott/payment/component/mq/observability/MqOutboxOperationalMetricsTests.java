package com.scott.payment.component.mq.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqOutboxOperationalMetricsTests
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 验证 MQ Outbox Gauge、批次饱和 Counter 和固定低基数标签。
 * @status : create
 */
class MqOutboxOperationalMetricsTests {

    @Test
    void shouldExposeReliableOutboxSnapshotAndSaturation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MqOutboxOperationalMetrics metrics =
                new MqOutboxOperationalMetrics(Optional.of(registry));
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 20, 0);

        metrics.updateReliable(3L, 2L, 1L, 4L, now.minusMinutes(5), now);
        metrics.recordBatchSize(MqOutboxOperationalMetrics.RELIABLE_OUTBOX, 100, 100);
        metrics.recordRelayDuration(
                MqOutboxOperationalMetrics.RELIABLE_OUTBOX, "success", 1_000_000L);

        assertThat(registry.find(MqOutboxOperationalMetrics.PENDING_COUNT)
                .tags("outbox_type", "reliable", "status", "INIT")
                .gauge().value()).isEqualTo(3D);
        assertThat(registry.find(MqOutboxOperationalMetrics.CLOSED_COUNT)
                .tags("outbox_type", "reliable", "topic_domain", "all")
                .gauge().value()).isEqualTo(4D);
        assertThat(registry.find(MqOutboxOperationalMetrics.OLDEST_AGE_SECONDS)
                .tag("outbox_type", "reliable").gauge().value()).isEqualTo(300D);
        assertThat(registry.find(MqOutboxOperationalMetrics.BATCH_SATURATED)
                .tag("outbox_type", "reliable").counter().count()).isEqualTo(1D);
        assertThat(registry.find(MqOutboxOperationalMetrics.RELAY_DURATION)
                .tags("outbox_type", "reliable", "outcome", "success")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void shouldExposeTransactionFailedAndClosedWithoutBusinessIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MqOutboxOperationalMetrics metrics =
                new MqOutboxOperationalMetrics(Optional.of(registry));
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 20, 0);

        metrics.updateTransaction(2L, 1L, 5L, 3L, now.minusSeconds(90), now);

        assertThat(registry.find(MqOutboxOperationalMetrics.PENDING_COUNT)
                .tags("outbox_type", "transaction", "status", "FAILED")
                .gauge().value()).isEqualTo(5D);
        assertThat(registry.find(MqOutboxOperationalMetrics.CLOSED_COUNT)
                .tags("outbox_type", "transaction", "topic_domain", "all")
                .gauge().value()).isEqualTo(3D);
        assertThat(registry.find(MqOutboxOperationalMetrics.OLDEST_AGE_SECONDS)
                .tag("outbox_type", "transaction").gauge().value()).isEqualTo(90D);
    }
}
