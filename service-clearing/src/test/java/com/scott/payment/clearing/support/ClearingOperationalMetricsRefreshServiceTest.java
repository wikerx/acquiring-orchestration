package com.scott.payment.clearing.support;

import com.scott.payment.clearing.entity.ClearingPendingMetricsDO;
import com.scott.payment.clearing.entity.ClearingReserveRemainingMetricsDO;
import com.scott.payment.clearing.mapper.ClearingOperationalMetricsMapper;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Gauge 刷新按季度聚合，并且失败时不会发布不完整快照。 */
class ClearingOperationalMetricsRefreshServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 2, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldAggregateEveryPublishedQuarterAndSkipFutureQuarter() {
        ClearingOperationalMetricsMapper mapper = mock(ClearingOperationalMetricsMapper.class);
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        TransactionShardingProperties properties = nodes("202601", "202602", "202603", "202604");
        ClearingOperationalMetricsRefreshService service =
                new ClearingOperationalMetricsRefreshService(mapper, properties, metrics, CLOCK);
        LocalDateTime q1 = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime q2 = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime q3 = LocalDateTime.of(2026, 7, 1, 0, 0);
        when(mapper.selectPendingByStatus(q1, q2, NOW))
                .thenReturn(List.of(pending("PENDING", 2, 120)));
        when(mapper.selectPendingByStatus(q2, q3, NOW))
                .thenReturn(List.of(pending("PENDING", 3, 240), pending("FAILED", 1, 60)));
        when(mapper.selectPendingByStatus(q3, q3.plusMonths(3), NOW))
                .thenReturn(List.of(pending("FAILED", 4, 300)));
        when(mapper.selectReserveRemainingByCurrency(q1, q2))
                .thenReturn(List.of(reserve("USD", "12.50")));
        when(mapper.selectReserveRemainingByCurrency(q2, q3))
                .thenReturn(List.of(reserve("USD", "7.50"), reserve("EUR", "3.00")));
        when(mapper.selectReserveRemainingByCurrency(q3, q3.plusMonths(3))).thenReturn(List.of());

        service.refresh();

        verify(metrics).updatePending(
                Map.of("PENDING", 5L, "FAILED", 5L),
                Map.of("PENDING", 240L, "FAILED", 300L));
        verify(metrics).updateReserveRemaining(
                Map.of("USD", new BigDecimal("20.00"), "EUR", new BigDecimal("3.00")));
        verify(metrics).recordMetricsRefresh(true);
        verify(mapper, never()).selectPendingByStatus(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0), NOW);
    }

    @Test
    void failedQuarterShouldKeepPreviousGaugeSnapshot() {
        ClearingOperationalMetricsMapper mapper = mock(ClearingOperationalMetricsMapper.class);
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        ClearingOperationalMetricsRefreshService service = new ClearingOperationalMetricsRefreshService(
                mapper, nodes("202601", "202602"), metrics, CLOCK);
        LocalDateTime q1 = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime q2 = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime q3 = LocalDateTime.of(2026, 7, 1, 0, 0);
        when(mapper.selectPendingByStatus(q1, q2, NOW)).thenReturn(List.of(pending("PENDING", 2, 120)));
        when(mapper.selectReserveRemainingByCurrency(q1, q2)).thenReturn(List.of());
        when(mapper.selectPendingByStatus(q2, q3, NOW))
                .thenThrow(new IllegalStateException("replica unavailable"));

        assertThatThrownBy(service::refresh).isInstanceOf(IllegalStateException.class);

        verify(metrics, never()).updatePending(org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap());
        verify(metrics, never()).updateReserveRemaining(org.mockito.ArgumentMatchers.anyMap());
        verify(metrics).recordMetricsRefresh(false);
    }

    @Test
    void quarterPublicationShouldUseRouteTimezoneWhileAgeUsesUtc() {
        ClearingOperationalMetricsMapper mapper = mock(ClearingOperationalMetricsMapper.class);
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        Clock boundaryClock = Clock.fixed(
                Instant.parse("2026-06-30T16:30:00Z"), ZoneId.of("UTC"));
        ClearingOperationalMetricsRefreshService service = new ClearingOperationalMetricsRefreshService(
                mapper, nodes("202603"), metrics, boundaryClock);
        LocalDateTime q3 = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime q4 = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime nowUtc = LocalDateTime.of(2026, 6, 30, 16, 30);
        when(mapper.selectPendingByStatus(q3, q4, nowUtc)).thenReturn(List.of());
        when(mapper.selectReserveRemainingByCurrency(q3, q4)).thenReturn(List.of());

        service.refresh();

        verify(mapper).selectPendingByStatus(q3, q4, nowUtc);
        verify(metrics).recordMetricsRefresh(true);
    }

    private TransactionShardingProperties nodes(String... nodes) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of(nodes));
        return properties;
    }

    private ClearingPendingMetricsDO pending(String status, long count, long oldestSeconds) {
        ClearingPendingMetricsDO row = new ClearingPendingMetricsDO();
        row.setClearingStatus(status);
        row.setPendingCount(count);
        row.setOldestPendingSeconds(oldestSeconds);
        return row;
    }

    private ClearingReserveRemainingMetricsDO reserve(String currency, String amount) {
        ClearingReserveRemainingMetricsDO row = new ClearingReserveRemainingMetricsDO();
        row.setReserveCurrency(currency);
        row.setRemainingAmount(new BigDecimal(amount));
        return row;
    }
}
