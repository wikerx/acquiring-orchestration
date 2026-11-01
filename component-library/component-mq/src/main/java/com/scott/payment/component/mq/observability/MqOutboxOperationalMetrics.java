package com.scott.payment.component.mq.observability;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通用和交易 MQ Outbox 的低基数 Micrometer 指标汇聚器，不允许使用商户号、交易号、事件号或异常正文作为标签。
 */
@Component
public class MqOutboxOperationalMetrics {

    /**
     * 待处理 Outbox 消息数量指标名称。
     */
    public static final String PENDING_COUNT = "acquiring.mq.outbox.pending.count";

    /**
     * 最早待处理 Outbox 消息等待秒数指标名称。
     */
    public static final String OLDEST_AGE_SECONDS = "acquiring.mq.outbox.oldest.age.seconds";

    /**
     * 已关闭 Outbox 消息数量指标名称。
     */
    public static final String CLOSED_COUNT = "acquiring.mq.outbox.closed.count";

    /**
     * 单批 Outbox 中继处理耗时指标名称。
     */
    public static final String RELAY_DURATION = "acquiring.mq.outbox.relay.duration";

    /**
     * Outbox 中继批次达到上限的计数指标名称。
     */
    public static final String BATCH_SATURATED = "acquiring.mq.outbox.batch.saturated";

    /**
     * 通用可靠消息 Outbox 的低基数指标标签值。
     */
    public static final String RELIABLE_OUTBOX = "reliable";

    /**
     * 交易消息 Outbox 的低基数指标标签值。
     */
    public static final String TRANSACTION_OUTBOX = "transaction";

    private static final String[] PENDING_STATUSES = {"INIT", "PROCESSING", "RETRY_WAIT", "FAILED"};

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> pending = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> closed = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> oldestAge = new ConcurrentHashMap<>();
    private final Map<String, Counter> saturated = new ConcurrentHashMap<>();
    private final Map<String, Timer> relayTimers = new ConcurrentHashMap<>();

    /**
     * 创建可选 Micrometer 指标汇聚器。
     */
    @Autowired
    public MqOutboxOperationalMetrics(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.orElse(null);
        registerOutbox(RELIABLE_OUTBOX);
        registerOutbox(TRANSACTION_OUTBOX);
    }

    /**
     * 创建不注册指标的实例，供不启动 Spring 容器的单元测试使用。
     */
    public static MqOutboxOperationalMetrics noop() {
        return new MqOutboxOperationalMetrics(Optional.empty());
    }

    /**
     * 更新通用 Outbox 当前状态快照。
     */
    public void updateReliable(long init,
                               long processing,
                               long retryWait,
                               long closedCount,
                               LocalDateTime oldestPendingTime,
                               LocalDateTime now) {
        update(RELIABLE_OUTBOX, init, processing, retryWait, 0L,
                closedCount, oldestPendingTime, now);
    }

    /**
     * 更新跨全部已发布季度汇总后的交易 Outbox 当前状态快照。
     */
    public void updateTransaction(long init,
                                  long processing,
                                  long failed,
                                  long closedCount,
                                  LocalDateTime oldestPendingTime,
                                  LocalDateTime now) {
        update(TRANSACTION_OUTBOX, init, processing, 0L, failed,
                closedCount, oldestPendingTime, now);
    }

    /**
     * 批次达到查询上限时累计饱和次数。
     */
    public void recordBatchSize(String outboxType, int selected, int batchSize) {
        if (selected >= Math.max(batchSize, 1)) {
            Counter counter = saturated.get(outboxType);
            if (counter != null) {
                counter.increment();
            }
        }
    }

    /**
     * 记录 Relay 批次耗时及固定结果标签。
     */
    public void recordRelayDuration(String outboxType, String outcome, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        String normalizedOutcome = "success".equals(outcome) ? "success" : "failure";
        Timer timer = relayTimers.computeIfAbsent(outboxType + ':' + normalizedOutcome,
                ignored -> Timer.builder(RELAY_DURATION)
                        .description("MQ Outbox relay batch duration")
                        .tag("outbox_type", outboxType)
                        .tag("outcome", normalizedOutcome)
                        .register(meterRegistry));
        timer.record(Math.max(durationNanos, 0L), TimeUnit.NANOSECONDS);
    }

    /**
     * 更新指定 Outbox 类型的低基数状态快照，负数统一收敛为零。
     *
     * @param outboxType 固定 Outbox 类型
     * @param init INIT 数量
     * @param processing PROCESSING 数量
     * @param retryWait RETRY_WAIT 数量
     * @param failed FAILED 数量
     * @param closedCount CLOSED 数量
     * @param oldestPendingTime 最早待投递事件时间
     * @param now 指标统计时间
     */
    private void update(String outboxType,
                        long init,
                        long processing,
                        long retryWait,
                        long failed,
                        long closedCount,
                        LocalDateTime oldestPendingTime,
                        LocalDateTime now) {
        value(outboxType, "INIT").set(Math.max(init, 0L));
        value(outboxType, "PROCESSING").set(Math.max(processing, 0L));
        value(outboxType, "RETRY_WAIT").set(Math.max(retryWait, 0L));
        value(outboxType, "FAILED").set(Math.max(failed, 0L));
        closed.get(outboxType).set(Math.max(closedCount, 0L));
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        long ageSeconds = oldestPendingTime == null
                ? 0L : Math.max(ChronoUnit.SECONDS.between(oldestPendingTime, actualNow), 0L);
        oldestAge.get(outboxType).set(ageSeconds);
    }

    /**
     * 为固定 Outbox 类型注册状态、积压时间、关闭数量和批次饱和指标。
     *
     * @param outboxType 固定 Outbox 类型
     */
    private void registerOutbox(String outboxType) {
        for (String status : PENDING_STATUSES) {
            AtomicLong holder = value(outboxType, status);
            if (meterRegistry != null) {
                Gauge.builder(PENDING_COUNT, holder, AtomicLong::get)
                        .description("MQ Outbox pending messages by fixed status")
                        .tag("outbox_type", outboxType)
                        .tag("status", status)
                        .register(meterRegistry);
            }
        }
        AtomicLong closedHolder = new AtomicLong();
        AtomicLong oldestHolder = new AtomicLong();
        closed.put(outboxType, closedHolder);
        oldestAge.put(outboxType, oldestHolder);
        if (meterRegistry != null) {
            Gauge.builder(CLOSED_COUNT, closedHolder, AtomicLong::get)
                    .description("MQ Outbox messages with exhausted delivery retries")
                    .tag("outbox_type", outboxType)
                    .tag("topic_domain", "all")
                    .register(meterRegistry);
            Gauge.builder(OLDEST_AGE_SECONDS, oldestHolder, AtomicLong::get)
                    .description("Age in seconds of the oldest pending MQ Outbox message")
                    .tag("outbox_type", outboxType)
                    .register(meterRegistry);
            saturated.put(outboxType, Counter.builder(BATCH_SATURATED)
                    .description("MQ Outbox scans that reached the configured batch limit")
                    .tag("outbox_type", outboxType)
                    .register(meterRegistry));
        }
    }

    /**
     * 获取或创建指定 Outbox 类型与状态组合的原子指标值。
     *
     * @param outboxType 固定 Outbox 类型
     * @param status 固定状态标签
     * @return 可由 Gauge 读取的原子数值
     */
    private AtomicLong value(String outboxType, String status) {
        return pending.computeIfAbsent(outboxType + ':' + status, ignored -> new AtomicLong());
    }
}
