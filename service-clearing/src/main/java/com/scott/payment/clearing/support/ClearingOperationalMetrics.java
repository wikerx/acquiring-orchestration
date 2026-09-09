package com.scott.payment.clearing.support;

import com.scott.payment.clearing.application.ClearingProcessingResult;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingOperationalMetrics
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分低基数运行指标。任何商户号、交易号、消息号和异常明文都不得作为 tag 传入。
 * @status : update
 */
@Component
public class ClearingOperationalMetrics {

    private static final Set<String> MODES = Set.of("DRY_RUN", "SHADOW_WRITE");
    private static final Set<String> ACTIONS = Set.of("RETRY", "REVIEW", "RECALCULATE");
    private static final Set<String> COMMAND_RESULTS = Set.of(
            "SCHEDULED", "ALREADY_SCHEDULED", "ESCALATED", "COMPLETED");
    private static final Set<String> MESSAGE_SOURCES = Set.of("TERMINAL", "RETRY_DUE");
    private static final Set<String> MESSAGE_REJECTION_REASONS = Set.of(
            "EMPTY", "DESERIALIZATION", "NULL_MESSAGE");
    private static final Set<String> TRANSACTION_TYPES = Set.of(
            "PAYMENT", "AUTHORIZATION", "PRE_AUTHORIZATION", "INCREMENTAL_AUTHORIZATION",
            "CAPTURE", "PRE_AUTH_COMPLETION", "VOID", "REFUND", "CHARGEBACK", "REPRESENTMENT");
    private static final Set<String> FEE_SOURCES = Set.of("SNAPSHOT", "REDIS", "SLAVE", "MASTER");
    private static final Set<String> TIER_RULE_TYPES = Set.of("COUNT", "AMOUNT");
    private static final Set<String> TIER_REPLAY_OUTCOMES = Set.of(
            "SUBMITTED", "REJECTED", "MANUAL_REVIEW", "RUNNING", "COMPLETED",
            "ITEM_FAILED", "BLOCKED_CLEARING");
    private static final Set<String> RESERVE_RELEASE_OUTCOMES = Set.of(
            "RELEASED", "ALREADY_FINAL", "NOT_DUE", "FAILED");
    private static final Set<String> RESERVE_ADJUSTMENT_OUTCOMES = Set.of(
            "SUBMITTED", "APPROVED", "REJECTED", "FAILED");
    private static final Set<ClearingStateEnum> PENDING_STATES = Set.of(
            ClearingStateEnum.NOT_CLEARED,
            ClearingStateEnum.PENDING,
            ClearingStateEnum.PROCESSING,
            ClearingStateEnum.WAITING_SOURCE,
            ClearingStateEnum.FAILED,
            ClearingStateEnum.MANUAL_REVIEW);
    /**
     * ISO币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；不允许为空；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");

    private final MeterRegistry registry;
    private final Map<ClearingStateEnum, AtomicLong> pendingCounts = new EnumMap<>(ClearingStateEnum.class);
    private final Map<ClearingStateEnum, AtomicLong> oldestPendingSeconds =
            new EnumMap<>(ClearingStateEnum.class);
    private final Map<String, AtomicReference<BigDecimal>> reserveRemainingAmounts =
            new ConcurrentHashMap<>();

    public ClearingOperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (ClearingStateEnum status : PENDING_STATES) {
            AtomicLong pendingCount = new AtomicLong();
            AtomicLong oldestSeconds = new AtomicLong();
            pendingCounts.put(status, pendingCount);
            oldestPendingSeconds.put(status, oldestSeconds);
            Gauge.builder("clearing.pending.count", pendingCount, AtomicLong::doubleValue)
                    .description("Current clearing actions awaiting completion")
                    .tag("status", status.name())
                    .register(registry);
            Gauge.builder("clearing.oldest.pending.seconds", oldestSeconds, AtomicLong::doubleValue)
                    .description("Age in seconds of the oldest unfinished clearing action")
                    .tag("status", status.name())
                    .register(registry);
        }
    }

    /** @param result 清分应用层有限结果枚举 */
    public void recordProcessing(ClearingProcessingResult result) {
        Counter.builder("clearing.processing")
                .description("Clearing processing outcomes")
                .tag("result", result.name())
                .register(registry)
                .increment();
    }

    /**
     * 记录一条已校验清分消息的低基数处理结果，并单独统计幂等重复类型。
     *
     * @param result 清分应用编排结果
     * @param transactionType 有限交易类型标签
     */
    public void recordEventConsumed(ClearingProcessingResult result, String transactionType) {
        String outcome = result == null ? "TECHNICAL_FAILURE" : result.name();
        Counter.builder("clearing.event.consumed")
                .description("Clearing input message outcomes")
                .tag("outcome", outcome)
                .tag("transaction_type", transactionType(transactionType))
                .register(registry)
                .increment();
        if (result == ClearingProcessingResult.ALREADY_CONSUMED
                || result == ClearingProcessingResult.ALREADY_COMPLETED
                || result == ClearingProcessingResult.RETRY_ALREADY_SCHEDULED
                || result == ClearingProcessingResult.STALE_RETRY_ACKNOWLEDGED) {
            Counter.builder("clearing.duplicate")
                    .description("Clearing duplicate or stale message outcomes")
                    .tag("duplicate_type", result.name())
                    .register(registry)
                    .increment();
        }
    }

    /**
     * 仅在阶段 B 已成功提交权威清分事实后统计完成动作。
     *
     * @param transactionType 有限交易类型标签
     * @param currency ISO 标签币种
     * @param clearingStatus 已提交的清分终态
     */
    public void recordCompleted(String transactionType, String currency, String clearingStatus) {
        Counter.builder("clearing.completed")
                .description("Successfully completed clearing actions")
                .tag("transaction_type", transactionType(transactionType))
                .tag("currency", currency(currency))
                .tag("status", clearingStatus(clearingStatus))
                .register(registry)
                .increment();
    }

    /**
     * 记录从消息进入应用编排到 ACK 或异常传播的完整耗时。
     *
     * @param outcome 有限处理结果标签
     * @param durationNanos 端到端耗时，单位纳秒
     */
    public void recordDuration(String outcome, long durationNanos) {
        Timer.builder("clearing.duration")
                .description("Clearing message processing duration")
                .tag("outcome", outcome == null || outcome.isBlank() ? "TECHNICAL_FAILURE" : outcome)
                .register(registry)
                .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }

    /**
     * 记录带固定失败码和可重试属性的受控失败。
     *
     * @param failureCode 清分受控失败枚举
     */
    public void recordFailure(ClearingFailureCodeEnum failureCode) {
        ClearingFailureCodeEnum safeCode = failureCode == null
                ? ClearingFailureCodeEnum.CLEARING_PERSISTENCE_ERROR : failureCode;
        Counter.builder("clearing.failure")
                .description("Controlled clearing failures")
                .tag("failure_code", safeCode.name())
                .tag("retryable", Boolean.toString(safeCode.isRetryable()))
                .register(registry)
                .increment();
    }

    /**
     * 记录动作费用配置最终采用的不可变事实来源。
     *
     * @param source 有限费用事实来源标签
     */
    public void recordFeeSource(String source) {
        Counter.builder("clearing.fee.cache.hit")
                .description("Source used to load an immutable clearing fee version")
                .tag("source", allowed(source, FEE_SOURCES))
                .register(registry)
                .increment();
    }

    /**
     * 记录阶梯规则批量初始化和行锁等待耗时。
     *
     * @param ruleType 有限阶梯规则类型
     * @param durationNanos 锁处理耗时，单位纳秒
     */
    public void recordTierLock(String ruleType, long durationNanos) {
        Timer.builder("clearing.tier.lock")
                .description("Clearing tier accumulator lock duration")
                .tag("rule_type", allowed(ruleType, TIER_RULE_TYPES))
                .register(registry)
                .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }

    /**
     * 记录阶梯期间重放有限状态；商户、交易和重放号不得作为 tag。
     *
     * @param outcome 有限重放结果标签
     */
    public void recordTierReplay(String outcome) {
        Counter.builder("clearing.tier.replay")
                .description("Tier period replay outcomes")
                .tag("outcome", allowed(outcome, TIER_REPLAY_OUTCOMES))
                .register(registry)
                .increment();
    }

    /**
     * 记录退款保证金返还结果；金额继续由保证金明细承担权威审计。
     *
     * @param currency 保证金原标签币种
     * @param outcome 有限返还结果标签
     */
    public void recordReserveReturn(String currency, String outcome) {
        Counter.builder("clearing.reserve.return")
                .description("Reserve return outcomes")
                .tag("currency", currency(currency))
                .tag("outcome", outcome == null || outcome.isBlank() ? "OTHER" : outcome)
                .register(registry)
                .increment();
    }

    /**
     * 记录到期保证金单条独立事务的有限结果，不记录业务身份和金额。
     *
     * @param outcome 有限释放结果标签
     */
    public void recordReserveRelease(String outcome) {
        Counter.builder("clearing.reserve.release")
                .description("Reserve release transaction outcomes")
                .tag("outcome", allowed(outcome, RESERVE_RELEASE_OUTCOMES))
                .register(registry)
                .increment();
    }

    /**
     * 记录人工保证金调整申请和双人复核的有限结果。
     *
     * @param outcome 有限调整结果标签
     */
    public void recordReserveAdjustment(String outcome) {
        Counter.builder("clearing.reserve.adjustment")
                .description("Reserve adjustment workflow outcomes")
                .tag("outcome", allowed(outcome, RESERVE_ADJUSTMENT_OUTCOMES))
                .register(registry)
                .increment();
    }

    /** 记录向 RocketMQ 原生重试传播的未知技术异常。 */
    public void recordTechnicalFailure() {
        Counter.builder("clearing.processing.failure")
                .description("Clearing technical failures propagated to RocketMQ")
                .register(registry)
                .increment();
    }

    /**
     * 记录 MQ 消息在进入清分应用层前被拒绝的固定分类，不接受业务标识或异常正文作为 tag。
     *
     * @param source TERMINAL 或 RETRY_DUE
     * @param reason EMPTY、DESERIALIZATION 或 NULL_MESSAGE
     */
    public void recordMessageRejected(String source, String reason) {
        Counter.builder("clearing.message.rejected")
                .description("Clearing messages rejected before application processing")
                .tag("source", allowed(source, MESSAGE_SOURCES))
                .tag("reason", allowed(reason, MESSAGE_REJECTION_REASONS))
                .register(registry)
                .increment();
    }

    /**
     * 记录一次补偿页面的数量统计。
     *
     * @param mode DRY_RUN 或 SHADOW_WRITE
     * @param scanned 扫描数
     * @param written 恢复写入数
     * @param skipped 幂等、过期或灰度跳过数
     */
    public void recordCompensation(String mode, int scanned, int written, int skipped) {
        String safeMode = allowed(mode, MODES);
        increment("clearing.compensation.scanned", "Compensation candidates scanned", safeMode, scanned);
        increment("clearing.compensation.written", "Compensation recoveries written", safeMode, written);
        increment("clearing.compensation.skipped", "Compensation candidates skipped", safeMode, skipped);
        Counter.builder("clearing.compensation.batch")
                .description("Clearing compensation batch outcomes")
                .tag("outcome", "SUCCESS")
                .tag("scan_type", safeMode)
                .register(registry)
                .increment();
    }

    /**
     * 记录已通过参数校验但在数据库扫描或逐条恢复阶段失败的补偿批次。
     *
     * @param mode DRY_RUN 或 SHADOW_WRITE 补偿模式
     */
    public void recordCompensationFailure(String mode) {
        Counter.builder("clearing.compensation.batch")
                .description("Clearing compensation batch outcomes")
                .tag("outcome", "FAILURE")
                .tag("scan_type", allowed(mode, MODES))
                .register(registry)
                .increment();
    }

    /**
     * 记录受控人工命令结果。
     *
     * @param action 固定命令类型
     * @param result 固定命令结果
     */
    public void recordCommand(String action, String result) {
        Counter.builder("clearing.command")
                .description("Clearing management command outcomes")
                .tag("action", allowed(action, ACTIONS))
                .tag("result", allowed(result, COMMAND_RESULTS))
                .register(registry)
                .increment();
    }

    /** @param anomalyType 固定清分异常分类 */
    public void recordAnomaly(ClearingAnomalyTypeEnum anomalyType) {
        Counter.builder("clearing.anomaly")
                .description("Clearing anomaly occurrences")
                .tag("type", anomalyType.name())
                .register(registry)
                .increment();
    }

    /**
     * 记录清分汇总、交易明细或保证金明细金额不平事件。
     *
     * @param currency 发生不平的 ISO 币种
     */
    public void recordAmountImbalance(String currency) {
        Counter.builder("clearing.amount.imbalance")
                .description("Clearing financial consistency mismatches")
                .tag("currency", currency(currency))
                .register(registry)
                .increment();
    }

    /**
     * 记录全部已发布季度 Gauge 刷新的有限成功或失败结果。
     *
     * @param success true 表示本轮全部刷新成功
     */
    public void recordMetricsRefresh(boolean success) {
        Counter.builder("clearing.metrics.refresh")
                .description("Clearing operational gauge refresh outcomes")
                .tag("result", success ? "SUCCESS" : "FAILURE")
                .register(registry)
                .increment();
    }

    /**
     * 使用全部已发布季度的聚合结果原子刷新待处理数量和最老等待时间。
     *
     * @param counts 按有限状态聚合的待处理数量
     * @param oldestSeconds 按有限状态聚合的最老等待秒数
     */
    public void updatePending(Map<String, Long> counts, Map<String, Long> oldestSeconds) {
        for (ClearingStateEnum status : PENDING_STATES) {
            pendingCounts.get(status).set(nonNegative(counts == null ? null : counts.get(status.name())));
            oldestPendingSeconds.get(status).set(
                    nonNegative(oldestSeconds == null ? null : oldestSeconds.get(status.name())));
        }
    }

    /**
     * 按标签币种刷新尚未释放或返还的保证金负债，仅用于容量和异常趋势监控。
     *
     * @param amounts 按 ISO 标签币种聚合的剩余保证金主单位金额
     */
    public void updateReserveRemaining(Map<String, BigDecimal> amounts) {
        reserveRemainingAmounts.values().forEach(value -> value.set(BigDecimal.ZERO));
        if (amounts == null) {
            return;
        }
        amounts.forEach((rawCurrency, amount) -> {
            String safeCurrency = currency(rawCurrency);
            BigDecimal safeAmount = amount == null || amount.signum() < 0 ? BigDecimal.ZERO : amount;
            reserveRemainingAmounts.computeIfAbsent(safeCurrency, key -> {
                AtomicReference<BigDecimal> reference = new AtomicReference<>(BigDecimal.ZERO);
                Gauge.builder("clearing.reserve.remaining.amount", reference,
                                value -> value.get().doubleValue())
                        .description("Outstanding reserve clearing liability by label currency")
                        .tag("currency", key)
                        .register(registry);
                return reference;
            }).set(safeAmount);
        });
    }

    private void increment(String name, String description, String mode, int amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder(name)
                .description(description)
                .tag("mode", mode)
                .register(registry)
                .increment(amount);
    }

    private String allowed(String value, Set<String> allowed) {
        return value != null && allowed.contains(value) ? value : "OTHER";
    }

    private String transactionType(String value) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        return allowed(normalized, TRANSACTION_TYPES);
    }

    /** 仅允许 ISO 三字母币种进入指标标签，非法值归一为 UNKNOWN。 */
    private String currency(String value) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        return normalized != null && ISO_CURRENCY.matcher(normalized).matches() ? normalized : "OTHER";
    }

    /**
     * 把清分状态规范化为受控指标标签，未知值统一归入 OTHER，避免指标基数失控。
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 当前方法生成或规范化后的文本值
     */
    private String clearingStatus(String value) {
        try {
            return ClearingStateEnum.valueOf(value).name();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return "OTHER";
        }
    }

    private long nonNegative(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }
}
