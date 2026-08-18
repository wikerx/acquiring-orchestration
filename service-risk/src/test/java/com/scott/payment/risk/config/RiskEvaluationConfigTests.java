package com.scott.payment.risk.config;

import com.scott.payment.component.core.trace.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        MDC.clear();
    }

    @Test
    void shouldUseClusterSafeFixedWindowDefaultsWithoutMigrationSwitches() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            RiskEvaluationProperties properties =
                    context.getBean(RiskEvaluationProperties.class);
            assertThat(properties.getFrequencyMaxWindowSeconds()).isEqualTo(86_400);
            assertThat(properties.getFrequencyMaxThresholdCount()).isEqualTo(1_000);
            assertThat(properties.getRuleSnapshotCapacityBypassTtlSeconds()).isEqualTo(30);
            assertThat(properties.isReadOnlyParallelEnabled()).isTrue();
            assertThat(properties.getReadOnlyParallelism()).isEqualTo(4);
            assertThat(properties.getReadOnlyQueueCapacity()).isEqualTo(64);
            assertThat(properties.getReadOnlyTimeoutMillis()).isEqualTo(3_000);
        });
    }

    @Test
    void shouldPropagateAndClearTraceContextInReadOnlyExecutor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ThreadPoolTaskExecutor executor = context.getBean(
                    "riskReadOnlyEvaluationExecutor",
                    ThreadPoolTaskExecutor.class);
            TraceContext.setTraceId("risk-parallel-trace-001");
            MDC.put("riskTestContext", "merchant-scope-001");
            Future<String> propagated = executor.submit(
                    () -> TraceContext.getTraceId() + ":" + MDC.get("riskTestContext"));

            assertThat(propagated.get(1, TimeUnit.SECONDS))
                    .isEqualTo("risk-parallel-trace-001:merchant-scope-001");

            TraceContext.clear();
            MDC.clear();
            Future<String> cleared = executor.submit(
                    () -> String.valueOf(TraceContext.getTraceId()) + ":" + MDC.get("riskTestContext"));
            assertThat(cleared.get(1, TimeUnit.SECONDS)).isEqualTo("null:null");
        });
    }

    @Test
    void shouldRestoreWorkerContextAfterDecoratedTaskCompletes() {
        RiskTraceContextTaskDecorator decorator = new RiskTraceContextTaskDecorator();
        TraceContext.setTraceId("submit-risk-trace");
        MDC.put("riskTestContext", "submit-scope");
        Runnable decorated = decorator.decorate(() -> {
            assertThat(TraceContext.getTraceId()).isEqualTo("submit-risk-trace");
            assertThat(MDC.get("riskTestContext")).isEqualTo("submit-scope");
        });

        TraceContext.setTraceId("worker-previous-trace");
        MDC.put("riskTestContext", "worker-previous-scope");
        decorated.run();

        assertThat(TraceContext.getTraceId()).isEqualTo("worker-previous-trace");
        assertThat(MDC.get("riskTestContext")).isEqualTo("worker-previous-scope");
    }

    @Test
    void shouldRejectReadOnlyParallelismBelowThreeGroups() {
        contextRunner
                .withPropertyValues("risk.evaluation.read-only-parallelism=2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("read-only-parallelism");
                });
    }

    @Test
    void shouldRejectNonPositiveReadOnlyQueueCapacity() {
        contextRunner
                .withPropertyValues("risk.evaluation.read-only-queue-capacity=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("read-only-queue-capacity");
                });
    }

    @Test
    void shouldRejectReadOnlyTimeoutBelowSafetyFloor() {
        contextRunner
                .withPropertyValues("risk.evaluation.read-only-timeout-millis=99")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("read-only-timeout-millis");
                });
    }

    @Test
    void shouldRejectSnapshotCapacityBypassTtlAboveAbsoluteMaximum() {
        log.info("测试快照容量旁路 TTL 上限，关键输入: 301 秒超过短期旁路绝对上限");
        contextRunner
                .withPropertyValues("risk.evaluation.rule-snapshot-capacity-bypass-ttl-seconds=301")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("rule-snapshot-capacity-bypass-ttl-seconds");
                });
        log.info("快照容量旁路 TTL 上限测试完成，结果: 启动门禁拒绝长期旁路配置");
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
