package com.scott.payment.risk.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationConfigTests
 * @date : 2026-07-30 23:15
 * @email : scott_x@163.com
 * @description : 验证数据库基线切换门禁及规则快照、固定频率窗口容量边界
 * @status : update
 */
@Slf4j
class RiskEvaluationConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RiskEvaluationConfig.class);

    @Test
    void shouldUseClusterSafeFixedWindowDefaultsWithoutMigrationSwitches() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            RiskEvaluationProperties properties =
                    context.getBean(RiskEvaluationProperties.class);
            assertThat(properties.getFrequencyMaxWindowSeconds()).isEqualTo(86_400);
            assertThat(properties.getFrequencyMaxThresholdCount()).isEqualTo(1_000);
        });
    }

    @Test
    void shouldRejectLifecycleBaselineWithoutExplicitCutoverConfirmation() {
        log.info("测试生命周期基线切换门禁，关键输入: LIFECYCLE 且未确认");
        contextRunner
                .withPropertyValues("risk.evaluation.baseline-mode=lifecycle")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("risk.evaluation.baseline-cutover-confirmed");
                });
    }

    @Test
    void shouldAllowBaselineShadowWithoutCutoverConfirmation() {
        contextRunner
                .withPropertyValues("risk.evaluation.baseline-mode=shadow")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RiskEvaluationProperties.class).getBaselineMode())
                            .isEqualTo(RiskBaselineMode.SHADOW);
                });
    }

    @Test
    void shouldRejectFrequencyWindowAboveAbsoluteMaximum() {
        contextRunner
                .withPropertyValues("risk.evaluation.frequency-max-window-seconds=604801")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("frequency-max-window-seconds");
                });
    }

    @Test
    void shouldRejectFrequencyThresholdAboveAbsoluteMaximum() {
        contextRunner
                .withPropertyValues("risk.evaluation.frequency-max-threshold-count=100001")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("frequency-max-threshold-count");
                });
    }
}
