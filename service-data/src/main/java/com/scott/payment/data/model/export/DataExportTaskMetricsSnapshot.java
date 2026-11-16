package com.scott.payment.data.model.export;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步导出任务全局运行状态快照，只包含监控聚合值，不包含账号或任务标识。
 */
@Data
public class DataExportTaskMetricsSnapshot {

    private long pendingCount;
    private long processingCount;
    private long successCount;
    private long failedCount;
    private long expiredCount;
    private LocalDateTime oldestUnfinishedTime;
}
