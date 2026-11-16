package com.scott.payment.data.observability.export;

import com.scott.payment.data.mapper.export.DataExportTaskMapper;
import com.scott.payment.data.model.export.DataExportTaskMetricsSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导出任务指标快照刷新边界测试。
 */
class DataExportOperationalMetricsRefresherTest {

    /**
     * 正常刷新必须把同一数据库快照和采样时间交给指标汇聚器。
     */
    @Test
    void shouldRefreshSnapshotFromDatabase() {
        DataExportTaskMapper taskMapper = mock(DataExportTaskMapper.class);
        DataExportOperationalMetrics operationalMetrics = mock(DataExportOperationalMetrics.class);
        DataExportTaskMetricsSnapshot snapshot = new DataExportTaskMetricsSnapshot();
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 8, 15);
        when(taskMapper.selectMetricsSnapshot()).thenReturn(snapshot);
        DataExportOperationalMetricsRefresher refresher =
                new DataExportOperationalMetricsRefresher(taskMapper, operationalMetrics);

        boolean refreshed = refresher.refresh(now);

        assertThat(refreshed).isTrue();
        verify(operationalMetrics).updateSnapshot(snapshot, now);
    }

    /**
     * 数据库或指标异常只能形成固定失败事件，不能中断服务启动和调度。
     */
    @Test
    void shouldContainSnapshotRefreshFailure() {
        DataExportTaskMapper taskMapper = mock(DataExportTaskMapper.class);
        DataExportOperationalMetrics operationalMetrics = mock(DataExportOperationalMetrics.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(taskMapper).selectMetricsSnapshot();
        DataExportOperationalMetricsRefresher refresher =
                new DataExportOperationalMetricsRefresher(taskMapper, operationalMetrics);

        boolean refreshed = refresher.refresh(LocalDateTime.now());

        assertThat(refreshed).isFalse();
        verify(operationalMetrics).recordEvent("snapshot_refresh_failed");
    }
}
