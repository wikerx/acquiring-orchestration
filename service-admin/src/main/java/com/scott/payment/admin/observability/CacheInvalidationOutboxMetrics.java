package com.scott.payment.admin.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CacheInvalidationOutboxMetrics
 * @date : 2026-07-31 10:20
 * @email : scott_x@163.com
 * @description : 缓存失效 Outbox 低基数指标记录器，以固定队列类型暴露到期批次、饱和和发布结果，不读取或输出事件业务标识
 * @status : create
 */
@Component
public class CacheInvalidationOutboxMetrics {

    /**
     * 最近一次到期事件查询返回数量。
     */
    public static final String DUE_BATCH_SIZE = "acquiring.redis.cache.invalidation.outbox.due.batch.size";

    /**
     * Outbox 批处理耗时。
     */
    public static final String BATCH_DURATION = "acquiring.redis.cache.invalidation.outbox.batch.duration";

    /**
     * Outbox 事件发布结果计数。
     */
    public static final String EVENT_TOTAL = "acquiring.redis.cache.invalidation.outbox.event";

    /**
     * 查询结果达到批次上限的次数；持续增长表示可能存在积压。
     */
    public static final String SATURATED_BATCH_TOTAL =
            "acquiring.redis.cache.invalidation.outbox.saturated.batch";

    /**
     * 无 MeterRegistry 场景使用的空实现。
     */
    private static final CacheInvalidationOutboxMetrics NOOP =
            new CacheInvalidationOutboxMetrics(null);

    /**
     * Micrometer 指标注册器；空实现中允许为空。
     */
    private final MeterRegistry meterRegistry;

    /**
     * 各固定 Outbox 最近一次到期批次大小。
     */
    private final Map<Outbox, AtomicLong> dueBatchSizes = new EnumMap<>(Outbox.class);

    /**
     * 创建缓存失效 Outbox 指标记录器并注册固定数量的 Gauge。
     *
     * @param meterRegistry Micrometer 指标注册器
     */
    public CacheInvalidationOutboxMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (Outbox outbox : Outbox.values()) {
            AtomicLong batchSize = new AtomicLong();
            dueBatchSizes.put(outbox, batchSize);
            if (meterRegistry != null) {
                Gauge.builder(DUE_BATCH_SIZE, batchSize, AtomicLong::get)
                        .description("Latest due cache invalidation outbox batch size")
                        .tag("outbox", tagValue(outbox))
                        .register(meterRegistry);
            }
        }
    }

    /**
     * 返回不产生指标副作用的空实现。
     *
     * @return Outbox 指标空实现
     */
    public static CacheInvalidationOutboxMetrics noop() {
        return NOOP;
    }

    /**
     * 记录一次成功完成的到期事件批处理。
     *
     * @param outbox       固定 Outbox 类型
     * @param dueCount     本次查询到期事件数
     * @param limit        单批查询上限
     * @param successCount 成功或幂等完成数
     * @param elapsedNanos 批处理耗时，单位纳秒
     */
    public void recordBatch(Outbox outbox,
                            int dueCount,
                            int limit,
                            int successCount,
                            long elapsedNanos) {
        AtomicLong batchSize = dueBatchSizes.get(outbox);
        if (batchSize != null) {
            batchSize.set(Math.max(0, dueCount));
        }
        if (meterRegistry == null) {
            return;
        }
        int normalizedDueCount = Math.max(0, dueCount);
        int normalizedSuccessCount = Math.max(0, Math.min(successCount, normalizedDueCount));
        int failedCount = normalizedDueCount - normalizedSuccessCount;
        BatchOutcome batchOutcome = failedCount == 0
                ? BatchOutcome.SUCCESS
                : BatchOutcome.PARTIAL;

        Timer.builder(BATCH_DURATION)
                .description("Cache invalidation outbox batch duration")
                .tags("outbox", tagValue(outbox), "outcome", tagValue(batchOutcome))
                .register(meterRegistry)
                .record(Math.max(0L, elapsedNanos), TimeUnit.NANOSECONDS);
        incrementEvents(outbox, EventOutcome.SUCCESS, normalizedSuccessCount);
        incrementEvents(outbox, EventOutcome.FAILED, failedCount);

        if (limit > 0 && normalizedDueCount >= limit) {
            Counter.builder(SATURATED_BATCH_TOTAL)
                    .description("Cache invalidation outbox saturated batch count")
                    .tag("outbox", tagValue(outbox))
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * 记录到期事件查询或批处理在返回结果前失败。
     *
     * @param outbox       固定 Outbox 类型
     * @param elapsedNanos 失败前耗时，单位纳秒
     */
    public void recordBatchError(Outbox outbox, long elapsedNanos) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(BATCH_DURATION)
                .description("Cache invalidation outbox batch duration")
                .tags(
                        "outbox", tagValue(outbox),
                        "outcome", tagValue(BatchOutcome.ERROR)
                )
                .register(meterRegistry)
                .record(Math.max(0L, elapsedNanos), TimeUnit.NANOSECONDS);
    }

    /**
     * 按固定结果累计事件数。
     *
     * @param outbox  Outbox 类型
     * @param outcome 事件结果
     * @param count   事件数量
     */
    private void incrementEvents(Outbox outbox, EventOutcome outcome, int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder(EVENT_TOTAL)
                .description("Cache invalidation outbox event count")
                .tags("outbox", tagValue(outbox), "outcome", tagValue(outcome))
                .register(meterRegistry)
                .increment(count);
    }

    /**
     * 将枚举转换为稳定的小写标签值。
     *
     * @param value 指标枚举
     * @return 小写标签值
     */
    private String tagValue(Enum<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("Outbox metric dimension can not be null");
        }
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 缓存失效 Outbox 类型。
     */
    public enum Outbox {
        /** 商户启停、密钥和 IP 策略变更后的安全缓存失效队列。 */
        MERCHANT_SECURITY,
        /** 风控规则发布后的 generation 切换与缓存失效队列。 */
        RISK_RULE
    }

    /**
     * Outbox 批处理结果。
     */
    public enum BatchOutcome {
        /** 本批到期事件全部成功或幂等完成。 */
        SUCCESS,
        /** 本批至少一个事件失败并保留待重试。 */
        PARTIAL,
        /** 查询或批处理在取得可信结果前异常终止。 */
        ERROR
    }

    /**
     * Outbox 单事件结果。
     */
    public enum EventOutcome {
        /** 单事件成功发布或命中幂等完成状态。 */
        SUCCESS,
        /** 单事件发布失败，仍由持久 Outbox 保留并重试。 */
        FAILED
    }
}
