package com.scott.payment.clearing.support;

import com.scott.payment.clearing.application.ClearingProcessingResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证清分指标只使用低基数业务结果，并按实际数量累计补偿扫描。 */
class ClearingOperationalMetricsTest {

    @Test
    void shouldRecordProcessingCompensationAndCommandCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ClearingOperationalMetrics metrics = new ClearingOperationalMetrics(registry);

        metrics.recordProcessing(ClearingProcessingResult.COMPLETED);
        metrics.recordTechnicalFailure();
        metrics.recordMessageRejected("TERMINAL", "DESERIALIZATION");
        metrics.recordCompensation("SHADOW_WRITE", 12, 4, 8);
        metrics.recordCompensationFailure("SHADOW_WRITE");
        metrics.recordCommand("RETRY", "SCHEDULED");

        assertThat(registry.get("clearing.processing").tag("result", "COMPLETED").counter().count())
                .isEqualTo(1D);
        assertThat(registry.get("clearing.processing.failure").counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.message.rejected").tag("source", "TERMINAL")
                .tag("reason", "DESERIALIZATION").counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.compensation.scanned").tag("mode", "SHADOW_WRITE")
                .counter().count()).isEqualTo(12D);
        assertThat(registry.get("clearing.compensation.written").tag("mode", "SHADOW_WRITE")
                .counter().count()).isEqualTo(4D);
        assertThat(registry.get("clearing.compensation.skipped").tag("mode", "SHADOW_WRITE")
                .counter().count()).isEqualTo(8D);
        assertThat(registry.get("clearing.compensation.batch").tag("outcome", "FAILURE")
                .tag("scan_type", "SHADOW_WRITE").counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.command").tag("action", "RETRY")
                .tag("result", "SCHEDULED").counter().count()).isEqualTo(1D);
    }

    @Test
    void unknownCommandTagsShouldCollapseToOther() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ClearingOperationalMetrics metrics = new ClearingOperationalMetrics(registry);

        metrics.recordCommand("merchant-M-1001", "transaction-TX-1001");
        metrics.recordMessageRejected("merchant-M-1001", "transaction-TX-1001");

        assertThat(registry.get("clearing.command").tag("action", "OTHER")
                .tag("result", "OTHER").counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.message.rejected").tag("source", "OTHER")
                .tag("reason", "OTHER").counter().count()).isEqualTo(1D);
    }

    @Test
    void shouldExposePendingReserveAndRefreshMetricsWithoutCrossCurrencyAggregation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ClearingOperationalMetrics metrics = new ClearingOperationalMetrics(registry);

        metrics.updatePending(Map.of("PENDING", 7L), Map.of("PENDING", 901L));
        metrics.updateReserveRemaining(Map.of(
                "USD", new BigDecimal("12.50"),
                "EUR", new BigDecimal("3.25")));
        metrics.recordMetricsRefresh(true);

        assertThat(registry.get("clearing.pending.count").tag("status", "PENDING").gauge().value())
                .isEqualTo(7D);
        assertThat(registry.get("clearing.oldest.pending.seconds").tag("status", "PENDING")
                .gauge().value()).isEqualTo(901D);
        assertThat(registry.get("clearing.reserve.remaining.amount").tag("currency", "USD")
                .gauge().value()).isEqualTo(12.5D);
        assertThat(registry.get("clearing.reserve.remaining.amount").tag("currency", "EUR")
                .gauge().value()).isEqualTo(3.25D);
        assertThat(registry.get("clearing.metrics.refresh").tag("result", "SUCCESS")
                .counter().count()).isEqualTo(1D);
    }

    @Test
    void shouldRecordFiniteReserveReleaseAndAdjustmentOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ClearingOperationalMetrics metrics = new ClearingOperationalMetrics(registry);

        metrics.recordReserveRelease("RELEASED");
        metrics.recordReserveRelease("ALREADY_FINAL");
        metrics.recordReserveRelease("NOT_DUE");
        metrics.recordReserveRelease("FAILED");
        metrics.recordReserveAdjustment("SUBMITTED");
        metrics.recordReserveAdjustment("APPROVED");
        metrics.recordReserveAdjustment("REJECTED");
        metrics.recordReserveAdjustment("FAILED");

        assertThat(registry.get("clearing.reserve.release").tag("outcome", "RELEASED")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.reserve.release").tag("outcome", "ALREADY_FINAL")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.reserve.release").tag("outcome", "NOT_DUE")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.reserve.release").tag("outcome", "FAILED")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.reserve.adjustment").tag("outcome", "SUBMITTED")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.reserve.adjustment").tag("outcome", "APPROVED")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.reserve.adjustment").tag("outcome", "REJECTED")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.get("clearing.reserve.adjustment").tag("outcome", "FAILED")
                .counter().count()).isEqualTo(1D);
    }
}
