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
 * @author : scott
 * @version : v1.0.0
 * @classname : MqOutboxOperationalMetrics
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 通用和交易 MQ Outbox 的低基数 Micrometer 指标汇聚器，不允许使用商户号、交易号、事件号或异常正文作为标签。
 * @status : create
 */
@Component
public class MqOutboxOperationalMetrics {

    /**
     * 等待计数，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String PENDING_COUNT = "acquiring.mq.outbox.pending.count";
    /**
     * {@code OLDEST_AGE_SECONDS}常量，统一 {@code MqOutboxOperationalMetrics} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String OLDEST_AGE_SECONDS = "acquiring.mq.outbox.oldest.age.seconds";
    /**
     * {@code CLOSED_COUNT}，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String CLOSED_COUNT = "acquiring.mq.outbox.closed.count";
    /**
     * {@code RELAY_DURATION}常量，统一 {@code MqOutboxOperationalMetrics} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String RELAY_DURATION = "acquiring.mq.outbox.relay.duration";
    /**
     * {@code BATCH_SATURATED}常量，统一 {@code MqOutboxOperationalMetrics} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String BATCH_SATURATED = "acquiring.mq.outbox.batch.saturated";

    /**
     * {@code RELIABLE_OUTBOX}常量，统一 {@code MqOutboxOperationalMetrics} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String RELIABLE_OUTBOX = "reliable";
    /**
     * {@code TRANSACTION_OUTBOX}常量，统一 {@code MqOutboxOperationalMetrics} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String TRANSACTION_OUTBOX = "transaction";

    private static final String[] PENDING_STATUSES = {"INIT", "PROCESSING", "RETRY_WAIT", "FAILED"};

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> pending = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> closed = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> oldestAge = new ConcurrentHashMap<>();
    private final Map<String, Counter> saturated = new ConcurrentHashMap<>();
    private final Map<String, Timer> relayTimers = new ConcurrentHashMap<>();

    /** 创建可选 Micrometer 指标汇聚器。 */
    @Autowired
    public MqOutboxOperationalMetrics(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.orElse(null);
        registerOutbox(RELIABLE_OUTBOX);
        registerOutbox(TRANSACTION_OUTBOX);
    }

    /** 创建不注册指标的实例，供不启动 Spring 容器的单元测试使用。 */
    public static MqOutboxOperationalMetrics noop() {
        return new MqOutboxOperationalMetrics(Optional.empty());
    }

    /** 更新通用 Outbox 当前状态快照。 */
    public void updateReliable(long init,
                               long processing,
                               long retryWait,
                               long closedCount,
                               LocalDateTime oldestPendingTime,
                               LocalDateTime now) {
        update(RELIABLE_OUTBOX, init, processing, retryWait, 0L,
                closedCount, oldestPendingTime, now);
    }

    /** 更新跨全部已发布季度汇总后的交易 Outbox 当前状态快照。 */
    public void updateTransaction(long init,
                                  long processing,
                                  long failed,
                                  long closedCount,
                                  LocalDateTime oldestPendingTime,
                                  LocalDateTime now) {
        update(TRANSACTION_OUTBOX, init, processing, 0L, failed,
                closedCount, oldestPendingTime, now);
    }

    /** 批次达到查询上限时累计饱和次数。 */
    public void recordBatchSize(String outboxType, int selected, int batchSize) {
        if (selected >= Math.max(batchSize, 1)) {
            Counter counter = saturated.get(outboxType);
            if (counter != null) {
                counter.increment();
            }
        }
    }

    /** 记录 Relay 批次耗时及固定结果标签。 */
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

    private AtomicLong value(String outboxType, String status) {
        return pending.computeIfAbsent(outboxType + ':' + status, ignored -> new AtomicLong());
    }
}
