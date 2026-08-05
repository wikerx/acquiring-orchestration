package com.scott.payment.risk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationConfig
 * @date : 2026-07-30 23:05
 * @email : scott_x@163.com
 * @description : 风控数据库基线启动门禁及规则快照、固定频率窗口容量校验
 * @status : update
 */
@Configuration
@EnableConfigurationProperties(RiskEvaluationProperties.class)
public class RiskEvaluationConfig {

    /**
     * 频率窗口允许配置的绝对最大时长，避免误配置绕过业务级容量上限。
     */
    private static final int ABSOLUTE_MAX_FREQUENCY_WINDOW_SECONDS = 7 * 24 * 60 * 60;

    /**
     * 频率规则阈值的绝对上限。
     */
    private static final int ABSOLUTE_MAX_FREQUENCY_THRESHOLD_COUNT = 100_000;

    /** 单个规则快照允许的绝对最大记录数。 */
    private static final int ABSOLUTE_MAX_RULE_SNAPSHOT_ROWS = 100_000;

    /** 单个序列化规则快照允许的绝对最大字符数，约 20 MiB。 */
    private static final int ABSOLUTE_MAX_RULE_SNAPSHOT_CHARACTERS = 20 * 1024 * 1024;

    /** 超容量快照旁路允许的绝对最大秒数，避免误配置长期跳过容量重新探测。 */
    private static final int ABSOLUTE_MAX_RULE_SNAPSHOT_CAPACITY_BYPASS_TTL_SECONDS = 300;

    /** 只读风控线程池允许的最大固定线程数。 */
    private static final int ABSOLUTE_MAX_READ_ONLY_PARALLELISM = 16;

    /** 只读风控线程池允许的最大等待队列容量。 */
    private static final int ABSOLUTE_MAX_READ_ONLY_QUEUE_CAPACITY = 10_000;

    /** 只读规则组允许的最小共享超时毫秒数。 */
    private static final long MIN_READ_ONLY_TIMEOUT_MILLIS = 100L;

    /** 只读规则组允许的最大共享超时毫秒数。 */
    private static final long MAX_READ_ONLY_TIMEOUT_MILLIS = 30_000L;

    /**
     * 校验数据库基线切换门禁及规则快照、固定频率窗口容量边界。
     *
     * @param properties 风控运行时配置
     * @return 仅用于让 Spring 在启动阶段执行校验的门禁标记
     * @throws IllegalStateException 未确认生产切换或频控容量配置越界时抛出
     */
    @Bean
    RiskEvaluationGuard riskEvaluationGuard(RiskEvaluationProperties properties) {
        if (properties.getBaselineMode() == RiskBaselineMode.LIFECYCLE
                && !properties.isBaselineCutoverConfirmed()) {
            throw new IllegalStateException(
                    "risk.evaluation.baseline-cutover-confirmed must be true before enabling lifecycle baseline");
        }
        validateRuleSnapshotCapacity(properties);
        validateFrequencyCapacity(properties);
        validateReadOnlyParallelCapacity(properties);
        return new RiskEvaluationGuard();
    }

    /**
     * 创建风控只读规则专用的固定有界线程池。
     *
     * <p>队列满时由请求线程执行任务以提供背压，不把任务提交到公共线程池；任务装饰器负责
     * 在工作线程恢复并清理链路上下文。累计限额、频控和 3DS 不得使用该执行器。</p>
     *
     * @param properties 风控并发容量配置
     * @return 只执行无副作用风控查询的专用执行器
     */
    @Bean(name = "riskReadOnlyEvaluationExecutor")
    ThreadPoolTaskExecutor riskReadOnlyEvaluationExecutor(RiskEvaluationProperties properties) {
        validateReadOnlyParallelCapacity(properties);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getReadOnlyParallelism());
        executor.setMaxPoolSize(properties.getReadOnlyParallelism());
        executor.setQueueCapacity(properties.getReadOnlyQueueCapacity());
        executor.setThreadNamePrefix("risk-read-only-");
        executor.setTaskDecorator(new RiskTraceContextTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /**
     * 校验完整规则快照的行数和序列化字符容量。
     *
     * @param properties 风控运行时配置
     * @throws IllegalStateException 行数或字符容量非正、超过绝对上限时抛出
     */
    private void validateRuleSnapshotCapacity(RiskEvaluationProperties properties) {
        if (properties.getRuleSnapshotMaxRows() <= 0
                || properties.getRuleSnapshotMaxRows() > ABSOLUTE_MAX_RULE_SNAPSHOT_ROWS) {
            throw new IllegalStateException(
                    "risk.evaluation.rule-snapshot-max-rows must be between 1 and "
                            + ABSOLUTE_MAX_RULE_SNAPSHOT_ROWS);
        }
        if (properties.getRuleSnapshotMaxCharacters() <= 0
                || properties.getRuleSnapshotMaxCharacters() > ABSOLUTE_MAX_RULE_SNAPSHOT_CHARACTERS) {
            throw new IllegalStateException(
                    "risk.evaluation.rule-snapshot-max-characters must be between 1 and "
                            + ABSOLUTE_MAX_RULE_SNAPSHOT_CHARACTERS);
        }
        if (properties.getRuleSnapshotCapacityBypassTtlSeconds() <= 0
                || properties.getRuleSnapshotCapacityBypassTtlSeconds()
                > ABSOLUTE_MAX_RULE_SNAPSHOT_CAPACITY_BYPASS_TTL_SECONDS) {
            throw new IllegalStateException(
                    "risk.evaluation.rule-snapshot-capacity-bypass-ttl-seconds must be between 1 and "
                            + ABSOLUTE_MAX_RULE_SNAPSHOT_CAPACITY_BYPASS_TTL_SECONDS);
        }
    }

    /**
     * 校验规则级固定窗口时长和阈值的部署上限。
     *
     * @param properties 风控运行时配置
     * @throws IllegalStateException 任一配置非正或超过绝对上限时抛出
     */
    private void validateFrequencyCapacity(RiskEvaluationProperties properties) {
        if (properties.getFrequencyMaxWindowSeconds() <= 0
                || properties.getFrequencyMaxWindowSeconds()
                > ABSOLUTE_MAX_FREQUENCY_WINDOW_SECONDS) {
            throw new IllegalStateException(
                    "risk.evaluation.frequency-max-window-seconds must be between 1 and "
                            + ABSOLUTE_MAX_FREQUENCY_WINDOW_SECONDS);
        }
        if (properties.getFrequencyMaxThresholdCount() <= 0
                || properties.getFrequencyMaxThresholdCount()
                > ABSOLUTE_MAX_FREQUENCY_THRESHOLD_COUNT) {
            throw new IllegalStateException(
                    "risk.evaluation.frequency-max-threshold-count must be between 1 and "
                            + ABSOLUTE_MAX_FREQUENCY_THRESHOLD_COUNT);
        }
    }

    /**
     * 校验只读规则并发度、队列容量和共享超时，防止配置退化为伪并发或无界等待。
     *
     * @param properties 风控运行时配置
     * @throws IllegalStateException 并发配置超出受控范围时抛出
     */
    private void validateReadOnlyParallelCapacity(RiskEvaluationProperties properties) {
        if (properties.getReadOnlyParallelism() < 3
                || properties.getReadOnlyParallelism() > ABSOLUTE_MAX_READ_ONLY_PARALLELISM) {
            throw new IllegalStateException(
                    "risk.evaluation.read-only-parallelism must be between 3 and "
                            + ABSOLUTE_MAX_READ_ONLY_PARALLELISM);
        }
        if (properties.getReadOnlyQueueCapacity() <= 0
                || properties.getReadOnlyQueueCapacity() > ABSOLUTE_MAX_READ_ONLY_QUEUE_CAPACITY) {
            throw new IllegalStateException(
                    "risk.evaluation.read-only-queue-capacity must be between 1 and "
                            + ABSOLUTE_MAX_READ_ONLY_QUEUE_CAPACITY);
        }
        if (properties.getReadOnlyTimeoutMillis() < MIN_READ_ONLY_TIMEOUT_MILLIS
                || properties.getReadOnlyTimeoutMillis() > MAX_READ_ONLY_TIMEOUT_MILLIS) {
            throw new IllegalStateException(
                    "risk.evaluation.read-only-timeout-millis must be between "
                            + MIN_READ_ONLY_TIMEOUT_MILLIS + " and " + MAX_READ_ONLY_TIMEOUT_MILLIS);
        }
    }

    /**
     * Spring 启动门禁标记，不承载运行时状态。
     */
    static final class RiskEvaluationGuard {
    }
}
