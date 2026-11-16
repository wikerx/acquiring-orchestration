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
 * 验证数据库基线切换门禁及规则快照、固定频率窗口容量边界
 */
@Slf4j
class RiskEvaluationConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RiskEvaluationConfig.class);

    /**
     * 清理用例写入的追踪上下文，避免线程复用污染后续断言。
     */
    @AfterEach
    void tearDown() {
        TraceContext.clear();
        MDC.clear();
    }

    /**
     * 验证默认配置使用集群安全固定窗口，并保持迁移开关关闭。
     */
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
            assertThat(properties.isReadQueryConsolidationEnabled()).isFalse();
        });
    }

    /**
     * 验证重复查询合并开关可由环境配置显式开启，代码默认仍保持兼容路径。
     */
    @Test
    void shouldBindReadQueryConsolidationSwitchExplicitly() {
        contextRunner
                .withPropertyValues("risk.evaluation.read-query-consolidation-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RiskEvaluationProperties.class)
                            .isReadQueryConsolidationEnabled()).isTrue();
                });
    }

    /**
     * 验证只读执行器向工作线程传递追踪上下文并在任务结束后清理。
     */
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

    /**
     * 验证装饰任务完成后恢复工作线程原有追踪上下文。
     */
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

    /**
     * 验证只读并发度不足以承载三个规则组时拒绝配置。
     */
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

    /**
     * 验证只读任务队列容量非正数时拒绝配置。
     */
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

    /**
     * 验证只读评估超时低于安全下限时拒绝配置。
     */
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

    /**
     * 验证容量旁路标记 TTL 超过绝对上限时拒绝配置。
     */
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

    /**
     * 验证未明确确认切换时禁止启用生命周期预占基线。
     */
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

    /**
     * 验证影子比较模式无需切换确认且不改变生产决策基线。
     */
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

    /**
     * 验证频控窗口超过绝对上限时拒绝配置。
     */
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

    /**
     * 验证频控阈值超过绝对上限时拒绝配置。
     */
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
