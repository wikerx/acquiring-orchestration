package com.scott.payment.risk.config;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskBaselineMode
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额数据库基线迁移模式。
 * @status : create
 */
public enum RiskBaselineMode {
    /** 使用交易主库实时汇总作为累计限额基线。 */
    LEGACY,

    /** 同时计算生命周期预占基线用于比对，但仍以主库实时汇总结果作决策。 */
    SHADOW,

    LIFECYCLE
}
