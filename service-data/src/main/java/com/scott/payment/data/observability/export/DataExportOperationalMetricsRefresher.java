package com.scott.payment.data.observability.export;

import com.scott.payment.data.mapper.export.DataExportTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定期从主库刷新异步导出任务快照，避免服务重启后 Gauge 长时间保留初始化零值。
 */
@Slf4j
@Component
public class DataExportOperationalMetricsRefresher {

    private final DataExportTaskMapper taskMapper;
    private final DataExportOperationalMetrics operationalMetrics;

    /**
     * 创建导出任务指标刷新器。
     */
    public DataExportOperationalMetricsRefresher(DataExportTaskMapper taskMapper,
                                                 DataExportOperationalMetrics operationalMetrics) {
        this.taskMapper = taskMapper;
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * 服务就绪后立即装载一次真实数据库快照，消除重启后的零值窗口。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void refreshAfterStartup() {
        refresh(LocalDateTime.now());
    }

    /**
     * 独立于导出调度持续刷新，确保没有任务调度请求时仍能发现历史失败和积压。
     */
    @Scheduled(
            initialDelayString = "${acquiring.data-export.metrics-refresh-delay-ms:30000}",
            fixedDelayString = "${acquiring.data-export.metrics-refresh-delay-ms:30000}"
    )
    public void refreshScheduled() {
        refresh(LocalDateTime.now());
    }

    /**
     * 刷新一次数据库快照；监控失败不得阻断服务启动或导出任务执行。
     *
     * @param now 当前业务时间
     * @return 是否刷新成功
     */
    public boolean refresh(LocalDateTime now) {
        try {
            operationalMetrics.updateSnapshot(taskMapper.selectMetricsSnapshot(), now);
            return true;
        } catch (RuntimeException exception) {
            operationalMetrics.recordEvent("snapshot_refresh_failed");
            log.warn("异步导出运行指标刷新失败, event: DATA_EXPORT_METRICS_REFRESH_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
