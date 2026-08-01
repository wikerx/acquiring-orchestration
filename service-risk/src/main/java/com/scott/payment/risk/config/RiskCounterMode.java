package com.scott.payment.risk.config;

/**
 * Redis 风控计数迁移模式。
 */
public enum RiskCounterMode {

    /**
     * 只使用历史 Key，保持当前生产行为。
     */
    LEGACY,

    /**
     * 历史 Key 参与决策，同时写入并观察 Cluster-safe Key。
     */
    SHADOW,

    /**
     * 只使用 Redis Cluster 同槽 Key。
     */
    CLUSTER_SAFE
}
