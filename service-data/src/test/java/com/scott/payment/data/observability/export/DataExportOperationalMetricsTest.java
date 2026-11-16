package com.scott.payment.data.observability.export;

import com.scott.payment.data.model.export.DataExportTaskMetricsSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证异步导出状态、生命周期和执行耗时指标的固定低基数契约。
 */
class DataExportOperationalMetricsTest {

    @Test
    void shouldExposeTaskSnapshotAndLifecycleWithoutBusinessIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DataExportOperationalMetrics metrics =
                new DataExportOperationalMetrics(Optional.of(registry));
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 2, 0);
        DataExportTaskMetricsSnapshot snapshot = new DataExportTaskMetricsSnapshot();
        snapshot.setPendingCount(3L);
        snapshot.setProcessingCount(2L);
        snapshot.setSuccessCount(8L);
        snapshot.setFailedCount(1L);
        snapshot.setExpiredCount(5L);
        snapshot.setOldestUnfinishedTime(now.minusMinutes(4));

        metrics.updateSnapshot(snapshot, now);
        metrics.recordEvents("claimed", 2L);
        metrics.recordExecution("success", 5_000_000L);

        assertThat(registry.find(DataExportOperationalMetrics.STATUS_COUNT)
                .tag("status", "PENDING").gauge().value()).isEqualTo(3D);
        assertThat(registry.find(DataExportOperationalMetrics.STATUS_COUNT)
                .tag("status", "FAILED").gauge().value()).isEqualTo(1D);
        assertThat(registry.find(DataExportOperationalMetrics.OLDEST_UNFINISHED_AGE)
                .gauge().value()).isEqualTo(240D);
        assertThat(registry.find(DataExportOperationalMetrics.EVENT_TOTAL)
                .tag("event", "claimed").counter().count()).isEqualTo(2D);
        assertThat(registry.find(DataExportOperationalMetrics.EXECUTION_DURATION)
                .tag("outcome", "success").timer().count()).isEqualTo(1L);
    }
}
