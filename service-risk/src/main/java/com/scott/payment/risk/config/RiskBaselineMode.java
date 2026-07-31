package com.scott.payment.risk.config;

/**
 * 商户累计限额数据库基线迁移模式。
 */
public enum RiskBaselineMode {
    /** 使用交易主库实时汇总作为累计限额基线。 */
    LEGACY,

    /** 同时计算生命周期预占基线用于比对，但仍以主库实时汇总结果作决策。 */
    SHADOW,

    LIFECYCLE
}
