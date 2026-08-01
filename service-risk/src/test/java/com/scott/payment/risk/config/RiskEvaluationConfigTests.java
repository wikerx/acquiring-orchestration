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
 * @description : 验证累计计数、生命周期基线和独立频率窗口的生产切换确认及容量启动门禁
 * @status : create
 */
@Slf4j
class RiskEvaluationConfigTests {

    /**
     * 仅装配风控迁移配置和启动门禁的轻量 Spring 上下文。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RiskEvaluationConfig.class);

    @Test
    void shouldRejectClusterSafeModeWithoutExplicitCutoverConfirmation() {
        log.info("测试累计同槽切换门禁，关键输入: CLUSTER_SAFE 且未确认");
        contextRunner
                .withPropertyValues("risk.evaluation.counter-mode=cluster-safe")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("risk.evaluation.counter-cutover-confirmed");
                });
        log.info("累计同槽切换门禁验证完成，结果: 启动被阻断");
    }

    @Test
    void shouldKeepLegacyAsTheDefaultCounterMode() {
        log.info("测试风控迁移默认值，关键输入: 无任何迁移配置");
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RiskEvaluationProperties.class).getCounterMode())
                    .isEqualTo(RiskCounterMode.LEGACY);
            assertThat(context.getBean(RiskEvaluationProperties.class).getFrequencyMode())
                    .isEqualTo(RiskFrequencyMode.LEGACY);
        });
        log.info("风控迁移默认值验证完成，结果: 累计计数和频率窗口均为 LEGACY");
    }

    @Test
    void shouldAllowShadowModeWithoutCutoverConfirmation() {
        log.info("测试累计 shadow 模式，关键输入: SHADOW 且未设置切换确认");
        contextRunner
                .withPropertyValues("risk.evaluation.counter-mode=shadow")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RiskEvaluationProperties.class).getCounterMode())
                            .isEqualTo(RiskCounterMode.SHADOW);
                });
        log.info("累计 shadow 模式验证完成，结果: 允许启动但不切换真实决策");
    }

    @Test
    void shouldAllowConfirmedClusterSafeMode() {
        log.info("测试累计同槽确认切换，关键输入: CLUSTER_SAFE 且已确认");
        contextRunner
                .withPropertyValues(
                        "risk.evaluation.counter-mode=cluster-safe",
                        "risk.evaluation.counter-cutover-confirmed=true"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RiskEvaluationProperties properties =
                            context.getBean(RiskEvaluationProperties.class);
                    assertThat(properties.getCounterMode()).isEqualTo(RiskCounterMode.CLUSTER_SAFE);
                    assertThat(properties.isCounterCutoverConfirmed()).isTrue();
                });
        log.info("累计同槽确认切换验证完成，结果: 允许启动");
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
        log.info("生命周期基线切换门禁验证完成，结果: 启动被阻断");
    }

    @Test
    void shouldAllowBaselineShadowWithoutCutoverConfirmation() {
        log.info("测试生命周期基线 shadow，关键输入: SHADOW 且未确认");
        contextRunner
                .withPropertyValues("risk.evaluation.baseline-mode=shadow")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RiskEvaluationProperties.class).getBaselineMode())
                            .isEqualTo(RiskBaselineMode.SHADOW);
                });
        log.info("生命周期基线 shadow 验证完成，结果: 保持历史基线决策");
    }

    @Test
    void shouldRejectSlidingWindowWithoutExplicitCutoverConfirmation() {
        log.info("测试频率滑动窗口切换门禁，关键输入: SLIDING_WINDOW 且未确认");
        contextRunner
                .withPropertyValues("risk.evaluation.frequency-mode=sliding-window")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("risk.evaluation.frequency-cutover-confirmed");
                });
        log.info("频率滑动窗口切换门禁验证完成，结果: 启动被阻断");
    }

    @Test
    void shouldAllowFrequencyShadowWithoutCutoverConfirmation() {
        log.info("测试频率窗口 shadow，关键输入: SHADOW 且未确认");
        contextRunner
                .withPropertyValues("risk.evaluation.frequency-mode=shadow")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RiskEvaluationProperties.class).getFrequencyMode())
                            .isEqualTo(RiskFrequencyMode.SHADOW);
                });
        log.info("频率窗口 shadow 验证完成，结果: 允许观察且保持固定窗口决策");
    }

    @Test
    void shouldAllowConfirmedSlidingWindowWithinCapacityBounds() {
        log.info("测试频率滑动窗口确认切换，关键输入: 已确认且容量配置有效");
        contextRunner
                .withPropertyValues(
                        "risk.evaluation.frequency-mode=sliding-window",
                        "risk.evaluation.frequency-cutover-confirmed=true",
                        "risk.evaluation.frequency-max-window-seconds=7200",
                        "risk.evaluation.frequency-max-threshold-count=500",
                        "risk.evaluation.frequency-max-members=1000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RiskEvaluationProperties properties =
                            context.getBean(RiskEvaluationProperties.class);
                    assertThat(properties.getFrequencyMode())
                            .isEqualTo(RiskFrequencyMode.SLIDING_WINDOW);
                    assertThat(properties.isFrequencyCutoverConfirmed()).isTrue();
                });
        log.info("频率滑动窗口确认切换验证完成，结果: 允许启动");
    }

    @Test
    void shouldRejectFrequencyThresholdAboveMemberCapacity() {
        log.info("测试频率容量门禁，关键输入: 最大阈值 2001 大于成员上限 2000");
        contextRunner
                .withPropertyValues(
                        "risk.evaluation.frequency-max-threshold-count=2001",
                        "risk.evaluation.frequency-max-members=2000"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("must not exceed frequency-max-members");
                });
        log.info("频率容量门禁验证完成，结果: 启动被阻断");
    }
}
