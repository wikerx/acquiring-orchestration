package com.scott.payment.risk.observability;

import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskShadowComparisonMonitor
 * @date : 2026-07-30 22:35
 * @email : scott_x@163.com
 * @description : 汇总风控数据库基线双轨比较结果，只记录计数和差异数量，不记录交易、商户或规则明细
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "risk.evaluation",
        name = "shadow-observation-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RiskShadowComparisonMonitor {

    /**
     * Redis 业务指标记录器，只记录 shadow 类型和比较结果。
     */
    private final RedisBusinessMetrics metrics;

    /**
     * 历史交易事实与生命周期预占事实已完成基线比较的次数。
     */
    private final LongAdder baselineCompared = new LongAdder();

    /**
     * 两种数据库基线金额不一致的次数。
     */
    private final LongAdder baselineMismatched = new LongAdder();

    /**
     * 创建带 Prometheus 观测的风控 shadow 比较器。
     *
     * @param metrics Redis 业务指标记录器
     */
    @Autowired
    public RiskShadowComparisonMonitor(RedisBusinessMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * 创建不产生指标副作用的 shadow 比较器，供纯单元测试直接构造。
     */
    public RiskShadowComparisonMonitor() {
        this(RedisBusinessMetrics.noop());
    }

    /**
     * 记录一次数据库基线双轨比较。
     *
     * @param legacyUnits    已成功交易事实汇总的最小金额单位
     * @param lifecycleUnits 生命周期预占事实汇总的最小金额单位
     */
    public void recordBaseline(long legacyUnits, long lifecycleUnits) {
        baselineCompared.increment();
        if (legacyUnits != lifecycleUnits) {
            baselineMismatched.increment();
            recordMetric(
                    RedisBusinessMetrics.Feature.RISK_BASELINE_SHADOW,
                    RedisBusinessMetrics.Outcome.MISMATCHED
            );
            return;
        }
        recordMetric(
                RedisBusinessMetrics.Feature.RISK_BASELINE_SHADOW,
                RedisBusinessMetrics.Outcome.MATCHED
        );
    }

    /**
     * 记录单次 shadow 比较结果，不写入规则、交易、商户或 Redis Key 维度。
     *
     * @param feature shadow 比较类型
     * @param outcome 一致、差异或不可用
     */
    private void recordMetric(RedisBusinessMetrics.Feature feature,
                              RedisBusinessMetrics.Outcome outcome) {
        metrics.recordOperation(
                feature,
                RedisBusinessMetrics.Operation.COMPARE,
                outcome,
                0L
        );
    }

    /**
     * 周期输出不包含业务标识的迁移观察摘要，并原子清零当前实例的区间计数。
     *
     * <p>汇总日志提供比较分母、差异和不可用次数；具体差异仍由业务仓储输出经过脱敏的
     * ruleId、limitType 或 counterKeyDigest，二者结合用于判断是否允许扩大灰度。</p>
     */
    @Scheduled(
            initialDelayString = "${risk.evaluation.shadow-observation-initial-delay-ms:60000}",
            fixedDelayString = "${risk.evaluation.shadow-observation-fixed-delay-ms:60000}")
    public void publishSummary() {
        RiskShadowComparisonSnapshot snapshot = snapshotAndReset();
        if (snapshot.totalObserved() == 0L) {
            return;
        }
        log.info(
                "event: RISK_BASELINE_SHADOW_COMPARISON_SUMMARY baselineCompared: {} baselineMismatched: {}",
                snapshot.baselineCompared(),
                snapshot.baselineMismatched()
        );
    }

    /**
     * 获取并清零当前观察区间，供调度输出和并发单元测试复用。
     *
     * @return 当前实例自上次快照后的各类比较计数
     */
    RiskShadowComparisonSnapshot snapshotAndReset() {
        return new RiskShadowComparisonSnapshot(
                baselineCompared.sumThenReset(),
                baselineMismatched.sumThenReset()
        );
    }

    /**
     * 单个服务实例在一个观察周期内的 shadow 比较摘要。
     *
     * @param baselineCompared      数据库基线完成比较数
     * @param baselineMismatched    数据库基线差异数
     */
    record RiskShadowComparisonSnapshot(long baselineCompared,
                                        long baselineMismatched) {

        /**
         * 计算当前周期内收到的全部比较或不可用事件数。
         *
         * @return 三类迁移路径的事件总数
         */
        long totalObserved() {
            return baselineCompared;
        }
    }
}
