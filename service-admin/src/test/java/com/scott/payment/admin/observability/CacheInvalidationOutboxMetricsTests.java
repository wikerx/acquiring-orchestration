package com.scott.payment.admin.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CacheInvalidationOutboxMetricsTests
 * @date : 2026-07-31 10:25
 * @email : scott_x@163.com
 * @description : 验证缓存失效 Outbox 的批次数量、饱和、事件结果和错误指标保持低基数
 * @status : create
 */
class CacheInvalidationOutboxMetricsTests {

    /**
     * 验证达到批次上限时更新 Gauge、累计饱和次数并区分成功和失败事件。
     */
    @Test
    void shouldRecordSaturatedPartialBatch() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CacheInvalidationOutboxMetrics metrics =
                new CacheInvalidationOutboxMetrics(registry);

        metrics.recordBatch(
                CacheInvalidationOutboxMetrics.Outbox.MERCHANT_SECURITY,
                10,
                10,
                8,
                Duration.ofMillis(25).toNanos()
        );

        assertThat(registry.find(CacheInvalidationOutboxMetrics.DUE_BATCH_SIZE)
                .tag("outbox", "merchant_security")
                .gauge()
                .value()).isEqualTo(10.0d);
        assertThat(registry.find(CacheInvalidationOutboxMetrics.SATURATED_BATCH_TOTAL)
                .tag("outbox", "merchant_security")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(registry.find(CacheInvalidationOutboxMetrics.EVENT_TOTAL)
                .tags("outbox", "merchant_security", "outcome", "success")
                .counter()
                .count()).isEqualTo(8.0d);
        assertThat(registry.find(CacheInvalidationOutboxMetrics.EVENT_TOTAL)
                .tags("outbox", "merchant_security", "outcome", "failed")
                .counter()
                .count()).isEqualTo(2.0d);
        assertThat(registry.find(CacheInvalidationOutboxMetrics.BATCH_DURATION)
                .tags("outbox", "merchant_security", "outcome", "partial")
                .timer()
                .count()).isEqualTo(1L);
    }

    /**
     * 验证数据库查询失败只记录固定 Outbox 类型和 error 结果。
     */
    @Test
    void shouldRecordBatchErrorWithoutBusinessTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CacheInvalidationOutboxMetrics metrics =
                new CacheInvalidationOutboxMetrics(registry);

        metrics.recordBatchError(
                CacheInvalidationOutboxMetrics.Outbox.RISK_RULE,
                Duration.ofMillis(5).toNanos()
        );

        assertThat(registry.find(CacheInvalidationOutboxMetrics.BATCH_DURATION)
                .tags("outbox", "risk_rule", "outcome", "error")
                .timer()
                .count()).isEqualTo(1L);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTag("merchantId")).isNull());
    }

    /**
     * 验证 Outbox 指标导出名称与 Prometheus backlog 和饱和告警规则一致。
     */
    @Test
    void shouldExportPrometheusMetricNamesUsedByAlerts() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        CacheInvalidationOutboxMetrics metrics =
                new CacheInvalidationOutboxMetrics(registry);

        metrics.recordBatch(
                CacheInvalidationOutboxMetrics.Outbox.RISK_RULE,
                10,
                10,
                9,
                Duration.ofMillis(8).toNanos()
        );

        assertThat(registry.scrape())
                .contains(
                        "acquiring_redis_cache_invalidation_outbox_due_batch_size",
                        "acquiring_redis_cache_invalidation_outbox_saturated_batch_total",
                        "acquiring_redis_cache_invalidation_outbox_event_total",
                        "outbox=\"risk_rule\""
                );
    }
}
