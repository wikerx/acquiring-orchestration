package com.scott.payment.risk.observability;

import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证数据库基线 shadow 观察计数在差异分类、并发记录和周期清零时保持准确。
 */
class RiskShadowComparisonMonitorTests {

    @Test
    void shouldClassifyBaselineComparisonsWithoutBusinessDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RiskShadowComparisonMonitor monitor = new RiskShadowComparisonMonitor(
                new RedisBusinessMetrics(registry));

        monitor.recordBaseline(20L, 20L);
        monitor.recordBaseline(20L, 21L);

        RiskShadowComparisonMonitor.RiskShadowComparisonSnapshot snapshot =
                monitor.snapshotAndReset();

        assertThat(snapshot.baselineCompared()).isEqualTo(2L);
        assertThat(snapshot.baselineMismatched()).isEqualTo(1L);
        assertThat(snapshot.totalObserved()).isEqualTo(2L);
        assertThat(registry.find(RedisBusinessMetrics.OPERATION_DURATION)
                .tags(
                        "feature", "risk_baseline_shadow",
                        "operation", "compare",
                        "outcome", "mismatched"
                )
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordConcurrentlyAndResetAtSnapshotBoundary() {
        RiskShadowComparisonMonitor monitor = new RiskShadowComparisonMonitor();

        IntStream.range(0, 1_000)
                .parallel()
                .forEach(ignored -> monitor.recordBaseline(100L, 100L));

        RiskShadowComparisonMonitor.RiskShadowComparisonSnapshot first =
                monitor.snapshotAndReset();
        RiskShadowComparisonMonitor.RiskShadowComparisonSnapshot second =
                monitor.snapshotAndReset();

        assertThat(first.baselineCompared()).isEqualTo(1_000L);
        assertThat(first.baselineMismatched()).isZero();
        assertThat(second.totalObserved()).isZero();
    }
}
