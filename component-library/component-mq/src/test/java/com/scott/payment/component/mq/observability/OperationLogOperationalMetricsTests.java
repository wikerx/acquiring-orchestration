package com.scott.payment.component.mq.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证操作日志指标只暴露固定系统、事件与结果标签。
 */
class OperationLogOperationalMetricsTests {

    @Test
    void shouldRecordFixedOperationLogEventsAndDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationLogOperationalMetrics metrics =
                new OperationLogOperationalMetrics(Optional.of(registry));

        metrics.recordEvent("ADMIN", "queued");
        metrics.recordEvent("untrusted-system", "untrusted-event");
        metrics.recordConsumeDuration("MERCHANT", "success", 2_000_000L);

        assertThat(registry.find(OperationLogOperationalMetrics.EVENT_TOTAL)
                .tags("system", "ADMIN", "event", "queued").counter().count()).isEqualTo(1D);
        assertThat(registry.find(OperationLogOperationalMetrics.EVENT_TOTAL)
                .tags("system", "UNKNOWN", "event", "failed").counter().count()).isEqualTo(1D);
        assertThat(registry.find(OperationLogOperationalMetrics.CONSUME_DURATION)
                .tags("system", "MERCHANT", "outcome", "success").timer().count()).isEqualTo(1L);
    }
}
