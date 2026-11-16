package com.scott.payment.settlement.domain.model;

/**
 * 已入账历史标签本金差额调整单状态；资金执行后不可回退，只能通过新的正向补偿单处理。
 */
public enum SettlementPrincipalCorrectionStatus {

    /** Maker 已冻结来源事实，等待不同账号复核。 */
    PENDING_REVIEW,

    /** Checker 已批准并完成差额资金入账，交易投影可异步修正。 */
    EXECUTED,

    /** Checker 已拒绝，冻结事实仅保留审计。 */
    REJECTED
}
