package com.scott.payment.risk.observability;

import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskShadowComparisonMonitorTests
 * @date : 2026-07-30 22:45
 * @email : scott_x@163.com
 * @description : 验证风控 shadow 观察计数在并发记录、差异分类和周期清零时保持准确
 * @status : create
 */
@Slf4j
class RiskShadowComparisonMonitorTests {

    /**
     * 验证三类 shadow 结果分别统计完成比较、差异和不可用，避免切换门禁缺少比较分母。
     */
    @Test
    void shouldClassifyShadowComparisonsWithoutBusinessDimensions() {
        log.info("测试 shadow 比较分类，关键输入: 累计/基线/频率各包含一致、差异或不可用样本");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RiskShadowComparisonMonitor monitor = new RiskShadowComparisonMonitor(
                new RedisBusinessMetrics(registry)
        );

        monitor.recordCumulative(10L, 10L);
        monitor.recordCumulative(10L, 11L);
        monitor.recordCumulative(10L, null);
        monitor.recordBaseline(20L, 20L);
        monitor.recordBaseline(20L, 21L);
        monitor.recordFrequency(3L, 3L);
        monitor.recordFrequency(3L, 4L);
        monitor.recordFrequency(3L, null);

        RiskShadowComparisonMonitor.RiskShadowComparisonSnapshot snapshot =
                monitor.snapshotAndReset();

        assertThat(snapshot.cumulativeCompared()).isEqualTo(2L);
        assertThat(snapshot.cumulativeMismatched()).isEqualTo(1L);
        assertThat(snapshot.cumulativeUnavailable()).isEqualTo(1L);
        assertThat(snapshot.baselineCompared()).isEqualTo(2L);
        assertThat(snapshot.baselineMismatched()).isEqualTo(1L);
        assertThat(snapshot.frequencyCompared()).isEqualTo(2L);
        assertThat(snapshot.frequencyMismatched()).isEqualTo(1L);
        assertThat(snapshot.frequencyUnavailable()).isEqualTo(1L);
        assertThat(snapshot.totalObserved()).isEqualTo(8L);
        assertThat(registry.find(RedisBusinessMetrics.OPERATION_DURATION)
                .tags(
                        "feature", "risk_cumulative_shadow",
                        "operation", "compare",
                        "outcome", "mismatched"
                )
                .timer()
                .count()).isEqualTo(1L);
        assertThat(registry.find(RedisBusinessMetrics.OPERATION_DURATION)
                .tags(
                        "feature", "risk_frequency_shadow",
                        "operation", "compare",
                        "outcome", "unavailable"
                )
                .timer()
                .count()).isEqualTo(1L);
        log.info("shadow 比较分类验证完成，结果: 8 个观察事件均进入对应计数");
    }

    /**
     * 验证并发记录不会丢失计数，并确认生成快照后下一个观察周期从零开始。
     */
    @Test
    void shouldRecordConcurrentlyAndResetAtSnapshotBoundary() {
        log.info("测试 shadow 并发计数，关键输入: 1000 次并行累计限额一致比较");
        RiskShadowComparisonMonitor monitor = new RiskShadowComparisonMonitor();

        IntStream.range(0, 1_000)
                .parallel()
                .forEach(ignored -> monitor.recordCumulative(100L, 100L));

        RiskShadowComparisonMonitor.RiskShadowComparisonSnapshot first =
                monitor.snapshotAndReset();
        RiskShadowComparisonMonitor.RiskShadowComparisonSnapshot second =
                monitor.snapshotAndReset();

        assertThat(first.cumulativeCompared()).isEqualTo(1_000L);
        assertThat(first.cumulativeMismatched()).isZero();
        assertThat(second.totalObserved()).isZero();
        log.info("shadow 并发计数验证完成，结果: 首周期 1000 次且快照后计数归零");
    }
}
