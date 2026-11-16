package com.scott.payment.component.mq.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 操作日志生产与消费链路的低基数指标汇聚器。
 *
 * <p>标签只允许使用受控系统编码、阶段和结果，禁止写入请求号、商户号、操作人、
 * 消息号、异常正文等业务或敏感信息。</p>
 */
@Component
public class OperationLogOperationalMetrics {

    /**
     * 操作日志链路事件计数指标。
     */
    public static final String EVENT_TOTAL = "acquiring.operation.log.event.total";

    /**
     * 操作日志单条消息消费耗时指标。
     */
    public static final String CONSUME_DURATION = "acquiring.operation.log.consume.duration";

    private static final Set<String> SYSTEM_CODES = Set.of("ADMIN", "MERCHANT");
    private static final Set<String> EVENT_TYPES = Set.of(
            "queued", "consumed", "duplicate", "invalid", "failed", "redis_fallback", "stale_claim");
    private static final Set<String> OUTCOMES = Set.of("success", "duplicate", "invalid", "failure");

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    /**
     * 创建可选 Micrometer 指标汇聚器。
     *
     * @param meterRegistry 当前应用指标注册器
     */
    @Autowired
    public OperationLogOperationalMetrics(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.orElse(null);
    }

    /**
     * 创建不注册指标的实例，供无 Spring 容器单元测试使用。
     *
     * @return 空指标汇聚器
     */
    public static OperationLogOperationalMetrics noop() {
        return new OperationLogOperationalMetrics(Optional.empty());
    }

    /**
     * 记录一个固定类型的操作日志链路事件。
     *
     * @param systemCode ADMIN 或 MERCHANT
     * @param eventType 固定事件类型
     */
    public void recordEvent(String systemCode, String eventType) {
        if (meterRegistry == null) {
            return;
        }
        String system = normalizeSystem(systemCode);
        String event = EVENT_TYPES.contains(eventType) ? eventType : "failed";
        counters.computeIfAbsent(system + ':' + event,
                        ignored -> Counter.builder(EVENT_TOTAL)
                                .description("Operation log producer and consumer events")
                                .tag("system", system)
                                .tag("event", event)
                                .register(meterRegistry))
                .increment();
    }

    /**
     * 记录操作日志消费耗时及受控结果。
     *
     * @param systemCode ADMIN 或 MERCHANT
     * @param outcome 固定消费结果
     * @param durationNanos 单调时钟耗时
     */
    public void recordConsumeDuration(String systemCode, String outcome, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        String system = normalizeSystem(systemCode);
        String normalizedOutcome = OUTCOMES.contains(outcome) ? outcome : "failure";
        timers.computeIfAbsent(system + ':' + normalizedOutcome,
                        ignored -> Timer.builder(CONSUME_DURATION)
                                .description("Operation log message consume duration")
                                .tag("system", system)
                                .tag("outcome", normalizedOutcome)
                                .register(meterRegistry))
                .record(Math.max(durationNanos, 0L), TimeUnit.NANOSECONDS);
    }

    /**
     * 将未知系统编码收敛为固定 UNKNOWN，避免外部输入制造高基数标签。
     */
    private String normalizeSystem(String systemCode) {
        return SYSTEM_CODES.contains(systemCode) ? systemCode : "UNKNOWN";
    }
}
