package com.scott.payment.risk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        return new RiskEvaluationGuard();
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
     * Spring 启动门禁标记，不承载运行时状态。
     */
    static final class RiskEvaluationGuard {
    }
}
