package com.scott.payment.component.redis.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisBusinessMetrics
 * @date : 2026-07-31 10:00
 * @email : scott_x@163.com
 * @description : Redis 治理业务指标门面，使用封闭枚举维度记录关键操作、降级与 Lua 失败，禁止业务标识进入 Prometheus 标签
 * @status : create
 */
public class RedisBusinessMetrics {

    /**
     * Redis 业务操作耗时指标，同时通过 Timer count 表达操作次数。
     */
    public static final String OPERATION_DURATION = "acquiring.redis.operation.duration";

    /**
     * Redis 降级计数指标。
     */
    public static final String FALLBACK_TOTAL = "acquiring.redis.fallback";

    /**
     * Redis Lua 执行失败计数指标。
     */
    public static final String LUA_FAILURE_TOTAL = "acquiring.redis.lua.failure";

    /**
     * 无 MeterRegistry 场景使用的空实现，供纯单元测试和未启用观测的离线工具复用。
     */
    private static final RedisBusinessMetrics NOOP = new RedisBusinessMetrics(null);

    /**
     * Micrometer 指标注册器；空实现中允许为空，生产自动配置始终传入有效实例。
     */
    private final MeterRegistry meterRegistry;

    /**
     * 创建 Redis 业务指标记录器。
     *
     * @param meterRegistry Micrometer 指标注册器
     */
    public RedisBusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 返回不产生指标副作用的空实现。
     *
     * @return Redis 指标空实现
     */
    public static RedisBusinessMetrics noop() {
        return NOOP;
    }

    /**
     * 记录一次 Redis 业务操作及其耗时。
     *
     * <p>所有标签均来自封闭枚举。调用方不得传入 Redis Key、merchantId、订单号、traceId、
     * 异常消息或其他无界业务值。</p>
     *
     * @param feature      Redis 业务能力
     * @param operation    操作类型
     * @param outcome      操作结果
     * @param elapsedNanos 单调时钟测得的耗时，单位纳秒
     */
    public void recordOperation(Feature feature,
                                Operation operation,
                                Outcome outcome,
                                long elapsedNanos) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(OPERATION_DURATION)
                .description("Redis business operation latency")
                .tags(
                        "feature", tagValue(feature),
                        "operation", tagValue(operation),
                        "outcome", tagValue(outcome)
                )
                .register(meterRegistry)
                .record(Math.max(0L, elapsedNanos), TimeUnit.NANOSECONDS);
    }

    /**
     * 记录一次 Redis 业务降级。
     *
     * @param feature Redis 业务能力
     * @param reason  有界降级原因
     */
    public void recordFallback(Feature feature, FallbackReason reason) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(FALLBACK_TOTAL)
                .description("Redis business fallback count")
                .tags(
                        "feature", tagValue(feature),
                        "reason", tagValue(reason)
                )
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录一次 Lua 执行失败。
     *
     * @param script  已登记 Lua 脚本
     * @param failure 有界失败分类
     */
    public void recordLuaFailure(Script script, Failure failure) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(LUA_FAILURE_TOTAL)
                .description("Redis Lua execution failure count")
                .tags(
                        "script", tagValue(script),
                        "failure", tagValue(failure)
                )
                .register(meterRegistry)
                .increment();
    }

    /**
     * 将运行时异常归并为有界 Lua 失败分类，不把异常类型或消息写入标签。
     *
     * @param throwable Redis 或 Lua 执行异常
     * @return 有界失败分类
     */
    public Failure classifyFailure(Throwable throwable) {
        if (throwable == null) {
            return Failure.EXECUTION;
        }
        String summary = (throwable.getClass().getName() + " " + throwable.getMessage())
                .toLowerCase(Locale.ROOT);
        if (summary.contains("timeout") || summary.contains("timed out")) {
            return Failure.TIMEOUT;
        }
        if (summary.contains("connection") || summary.contains("connect")) {
            return Failure.CONNECTION;
        }
        return Failure.EXECUTION;
    }

    /**
     * 将 Redis 运行时异常归并为有界降级原因。
     *
     * @param throwable Redis 调用异常
     * @return 有界降级原因
     */
    public FallbackReason classifyFallback(Throwable throwable) {
        return switch (classifyFailure(throwable)) {
            case CONNECTION -> FallbackReason.CONNECTION_FAILURE;
            case TIMEOUT -> FallbackReason.TIMEOUT;
            default -> FallbackReason.OPERATION_FAILURE;
        };
    }

    /**
     * 将枚举名称转换为稳定的小写 Prometheus 标签值。
     *
     * @param value 指标枚举值
     * @return 小写标签值
     */
    private String tagValue(Enum<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("Redis metric dimension can not be null");
        }
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Redis 业务能力维度，枚举成员数量即标签最大基数。
     */
    public enum Feature {
        /** Spring Cache 的读取、写入、清理及异常路径。 */
        CACHE,
        /** 缓存空值标记的命中、未命中和故障回源路径。 */
        CACHE_MISS_MARKER,
        /** 安全缓存提交后失效及门禁校验路径。 */
        CACHE_INVALIDATION,
        /** 缓存 generation 的读取、发布和回滚路径。 */
        CACHE_GENERATION,
        /** Redis 分布式锁的获取、竞争和释放路径。 */
        LOCK,
        /** 单 Key 业务幂等资格的获取路径。 */
        IDEMPOTENCY,
        /** MQ 消费辅助去重的获取与数据库兜底路径。 */
        MQ_DEDUP,
        /** Redis 强依赖的全局编号生成路径。 */
        GLOBAL_ID,
        /** 风控频率限制 Lua 执行路径。 */
        RISK_FREQUENCY,
        /** 风控累计限额预占与回滚路径。 */
        RISK_CUMULATIVE_LIMIT,
        /** 累计限额数据库基线的 shadow 比较路径。 */
        RISK_BASELINE_SHADOW
    }

    /**
     * Redis 操作类型维度。
     */
    public enum Operation {
        /** 读取缓存、状态或 generation。 */
        READ,
        /** 写入缓存、状态或 generation。 */
        WRITE,
        /** 精确失效单个业务缓存。 */
        EVICT,
        /** 清理受控缓存范围。 */
        CLEAR,
        /** 获取锁、幂等资格或发布门禁。 */
        ACQUIRE,
        /** 释放锁、幂等资格或发布门禁。 */
        RELEASE,
        /** 执行已登记的 Redis Lua 脚本。 */
        EXECUTE,
        /** 比较 shadow 新旧路径结果。 */
        COMPARE,
        /** 评估缓存状态或风险规则。 */
        EVALUATE
    }

    /**
     * Redis 操作结果维度。
     */
    public enum Outcome {
        /** 操作按预期完成。 */
        SUCCESS,
        /** 缓存或去重状态命中。 */
        HIT,
        /** 缓存或去重状态未命中。 */
        MISS,
        /** 幂等或 MQ 去重识别为重复请求。 */
        DUPLICATE,
        /** 锁或发布门禁已被其他持有者占用。 */
        CONTENDED,
        /** generation 发布中，读路径必须绕过缓存。 */
        PENDING,
        /** shadow 新旧结果一致。 */
        MATCHED,
        /** shadow 新旧结果不一致。 */
        MISMATCHED,
        /** shadow 任一路径不可用，无法完成可信比较。 */
        UNAVAILABLE,
        /** Redis 能力降级到数据库或既定兜底路径。 */
        FALLBACK,
        /** Redis 操作失败且不能归为正常降级结果。 */
        ERROR
    }

    /**
     * Redis 降级原因维度。
     */
    public enum FallbackReason {
        /** 可选 Redis 客户端未装配。 */
        CLIENT_MISSING,
        /** Redis 连接建立或保持失败。 */
        CONNECTION_FAILURE,
        /** Redis 命令在预算内未完成。 */
        TIMEOUT,
        /** 有界集合、桶或窗口达到容量上限。 */
        CAPACITY_EXCEEDED,
        /** Redis 或 Lua 返回未登记的结果。 */
        UNEXPECTED_RESULT,
        /** 无法进一步分类的 Redis 操作失败。 */
        OPERATION_FAILURE
    }

    /**
     * 已登记 Lua 脚本维度。
     */
    public enum Script {
        /** 仅允许 token 持有者释放缓存门禁租约的脚本。 */
        TOKEN_LEASE_RELEASE,
        /** MQ 双时间桶去重资格获取脚本。 */
        MQ_DEDUP_ACQUIRE,
        /** 基于 Redis TIME 生成全局编号序列的脚本。 */
        GLOBAL_ID_SEQUENCE,
        /** 原子读取或初始化当前缓存 generation 的脚本。 */
        CACHE_GENERATION_READ,
        /** 获取 generation 发布门禁的脚本。 */
        CACHE_GENERATION_BEGIN,
        /** 切换 generation 并释放发布门禁的脚本。 */
        CACHE_GENERATION_COMMIT,
        /** 风控兼容固定窗口频率计数脚本。 */
        RISK_FREQUENCY_FIXED,
        /** 风控累计限额预占脚本。 */
        RISK_CUMULATIVE_RESERVE,
        /** 风控累计限额预占回滚脚本。 */
        RISK_CUMULATIVE_ROLLBACK
    }

    /**
     * Lua 失败类型维度。
     */
    public enum Failure {
        /** Redis 连接不可用。 */
        CONNECTION,
        /** Lua 执行超过调用预算。 */
        TIMEOUT,
        /** Lua 返回空值或未登记的结果码。 */
        INVALID_RESULT,
        /** Lua 保护的有界结构达到容量限制。 */
        CAPACITY,
        /** 其他脚本执行、类型或服务端错误。 */
        EXECUTION
    }
}
