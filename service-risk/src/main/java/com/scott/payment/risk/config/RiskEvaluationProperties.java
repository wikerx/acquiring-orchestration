package com.scott.payment.risk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationProperties
 * @date : 2026-07-30 23:05
 * @email : scott_x@163.com
 * @description : 风控运行时、规则快照、累计限额生命周期和交易频率窗口的配置边界
 * @status : update
 */
@Data
@ConfigurationProperties(prefix = "risk.evaluation")
public class RiskEvaluationProperties {

    /**
     * 是否启用运行时名单和规则库。
     */
    private boolean runtimeEnabled = true;

    /**
     * Redis 命中缓存秒数。
     */
    private long cacheHitTtlSeconds = 300;

    /**
     * Redis 未命中缓存秒数。
     */
    private long cacheMissTtlSeconds = 60;

    /**
     * 风控名单和配置规则缓存迁移模式；生产默认保持 LEGACY，dev/test 可启用 SNAPSHOT。
     */
    private RiskRuleCacheMode ruleCacheMode = RiskRuleCacheMode.LEGACY;

    /**
     * 单个风控快照最多允许的数据库记录数，查询使用上限加一识别越界。
     */
    private int ruleSnapshotMaxRows = 5_000;

    /**
     * 单个序列化风控快照最大字符数，防止错误配置形成 Redis 大 Value。
     */
    private int ruleSnapshotMaxCharacters = 5 * 1024 * 1024;

    /**
     * 累计限额和频率计数的 Redis Key 迁移模式。
     */
    private RiskCounterMode counterMode = RiskCounterMode.LEGACY;

    /**
     * 是否已完成完整观察期并确认切换 Cluster-safe 计数。
     */
    private boolean counterCutoverConfirmed;

    /**
     * 交易频率窗口迁移模式；与累计金额计数独立，默认不改变历史固定窗口决策。
     */
    private RiskFrequencyMode frequencyMode = RiskFrequencyMode.LEGACY;

    /**
     * 是否已完成滑动窗口观察并批准其参与真实频控决策。
     */
    private boolean frequencyCutoverConfirmed;

    /**
     * 单条频率规则允许的最大窗口秒数，超过后进入 REVIEW 而不执行 Redis 脚本。
     */
    private int frequencyMaxWindowSeconds = 86_400;

    /**
     * 单条频率规则允许的最大阈值，防止错误配置形成不可控高基数窗口。
     */
    private int frequencyMaxThresholdCount = 1_000;

    /**
     * 单个频率 ZSet 允许保留的最大交易摘要成员数，达到上限时保守进入 REVIEW。
     */
    private int frequencyMaxMembers = 2_000;

    /**
     * Redis 周期累计值首次初始化时使用的数据库基线口径。
     */
    private RiskBaselineMode baselineMode = RiskBaselineMode.LEGACY;

    /**
     * 是否已经完成完整最大周期观察并确认启用生命周期基线。
     */
    private boolean baselineCutoverConfirmed;

    /**
     * 审计 MQ 是否启用。
     */
    private boolean auditMqEnabled = true;

    /**
     * MQ 消费幂等秒数。
     */
    private long auditConsumeIdempotentTtlSeconds = 604800;

    /**
     * 是否保留本地骨架规则作为无库或无命中兜底。
     */
    private boolean skeletonFallbackEnabled = true;

    /**
     * PREPARING 进入自愈扫描前的最短等待秒数。
     */
    private long reservationPreparingTimeoutSeconds = 60;

    /**
     * payment 记录仍不存在时允许本地事务完成的宽限秒数。
     */
    private long reservationPaymentAbsenceGraceSeconds = 300;

    /**
     * 是否消费 payment 终态事件并推进预占生命周期。
     */
    private boolean reservationEventConsumerEnabled = true;

    /**
     * 是否启用超时预占周期对账。
     */
    private boolean reservationReconcileEnabled = true;

    /**
     * 单次超时预占对账最大记录数。
     */
    private int reservationReconcileBatchSize = 100;
}
