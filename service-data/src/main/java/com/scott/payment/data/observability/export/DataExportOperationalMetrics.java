package com.scott.payment.data.observability.export;

import com.scott.payment.data.model.export.DataExportTaskMetricsSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步导出任务运行指标，所有标签均为固定枚举，禁止使用任务号、账号或商户号。
 */
@Component
public class DataExportOperationalMetrics {

    public static final String STATUS_COUNT = "acquiring.data.export.task.status.count";
    public static final String OLDEST_UNFINISHED_AGE = "acquiring.data.export.task.oldest.unfinished.age.seconds";
    public static final String EVENT_TOTAL = "acquiring.data.export.event.total";
    public static final String EXECUTION_DURATION = "acquiring.data.export.execution.duration";

    private static final Set<String> STATUSES = Set.of("PENDING", "PROCESSING", "SUCCESS", "FAILED", "EXPIRED");
    private static final Set<String> EVENTS = Set.of(
            "created", "duplicate", "retried", "claimed", "submitted", "deferred",
            "lease_recovered", "expired", "cleanup_failed", "download_success", "download_failed",
            "execution_success", "execution_failed", "snapshot_refresh_failed", "failed");

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> statusCounts = new ConcurrentHashMap<>();
    private final AtomicLong oldestUnfinishedAge = new AtomicLong();
    private final Map<String, Counter> eventCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> executionTimers = new ConcurrentHashMap<>();

    /**
     * 创建可选 Micrometer 指标汇聚器。
     */
    @Autowired
    public DataExportOperationalMetrics(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.orElse(null);
        for (String status : STATUSES) {
            AtomicLong holder = statusCounts.computeIfAbsent(status, ignored -> new AtomicLong());
            if (this.meterRegistry != null) {
                Gauge.builder(STATUS_COUNT, holder, AtomicLong::get)
                        .description("Data export task counts by fixed status")
                        .tag("status", status)
                        .register(this.meterRegistry);
            }
        }
        if (this.meterRegistry != null) {
            Gauge.builder(OLDEST_UNFINISHED_AGE, oldestUnfinishedAge, AtomicLong::get)
                    .description("Age in seconds of the oldest pending or processing export task")
                    .register(this.meterRegistry);
        }
    }

    /**
     * 创建不注册指标的测试实例。
     */
    public static DataExportOperationalMetrics noop() {
        return new DataExportOperationalMetrics(Optional.empty());
    }

    /**
     * 使用数据库聚合结果更新当前状态快照。
     */
    public void updateSnapshot(DataExportTaskMetricsSnapshot snapshot, LocalDateTime now) {
        if (snapshot == null) {
            return;
        }
        value("PENDING").set(Math.max(snapshot.getPendingCount(), 0L));
        value("PROCESSING").set(Math.max(snapshot.getProcessingCount(), 0L));
        value("SUCCESS").set(Math.max(snapshot.getSuccessCount(), 0L));
        value("FAILED").set(Math.max(snapshot.getFailedCount(), 0L));
        value("EXPIRED").set(Math.max(snapshot.getExpiredCount(), 0L));
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        long ageSeconds = snapshot.getOldestUnfinishedTime() == null ? 0L
                : Math.max(ChronoUnit.SECONDS.between(snapshot.getOldestUnfinishedTime(), actualNow), 0L);
        oldestUnfinishedAge.set(ageSeconds);
    }

    /**
     * 记录固定类型的导出生命周期事件。
     */
    public void recordEvent(String event) {
        recordEvents(event, 1L);
    }

    /**
     * 批量累计固定类型事件，避免调度批次逐项创建或查询计数器。
     */
    public void recordEvents(String event, long amount) {
        if (meterRegistry == null) {
            return;
        }
        String normalizedEvent = EVENTS.contains(event) ? event : "failed";
        eventCounters.computeIfAbsent(normalizedEvent,
                        ignored -> Counter.builder(EVENT_TOTAL)
                                .description("Data export task lifecycle events")
                                .tag("event", normalizedEvent)
                                .register(meterRegistry))
                .increment(Math.max(amount, 0L));
    }

    /**
     * 记录单个导出任务的执行耗时。
     */
    public void recordExecution(String outcome, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        String normalizedOutcome = "success".equals(outcome) ? "success" : "failure";
        executionTimers.computeIfAbsent(normalizedOutcome,
                        ignored -> Timer.builder(EXECUTION_DURATION)
                                .description("Single data export task execution duration")
                                .tag("outcome", normalizedOutcome)
                                .register(meterRegistry))
                .record(Math.max(durationNanos, 0L), TimeUnit.NANOSECONDS);
    }

    private AtomicLong value(String status) {
        return statusCounts.computeIfAbsent(status, ignored -> new AtomicLong());
    }
}
