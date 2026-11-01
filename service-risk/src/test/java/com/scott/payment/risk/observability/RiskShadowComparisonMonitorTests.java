package com.scott.payment.risk.observability;

import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证数据库基线 shadow 观察计数在差异分类、并发记录和周期清零时保持准确。
 */
class RiskShadowComparisonMonitorTests {

    /**
     * 验证 Spring 选择带指标依赖的生产构造器，而不是无指标的测试构造器。
     */
    @Test
    void shouldSelectProductionConstructorWhenCreatedBySpring() {
        RedisBusinessMetrics metrics = RedisBusinessMetrics.noop();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RedisBusinessMetrics.class, () -> metrics);
            context.register(RiskShadowComparisonMonitor.class);

            context.refresh();

            assertThat(context.getBean(RiskShadowComparisonMonitor.class)).isNotNull();
        }
    }

    /**
     * 验证基线一致和差异指标只使用受控维度，不携带业务标识。
     */
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

    /**
     * 验证并发计数准确，并在快照边界原子清零观察区间。
     */
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
