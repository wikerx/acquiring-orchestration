package com.scott.payment.settlement.domain.model;

/**
 * 定义独立结算冲正申请的 Maker-Checker 状态；APPROVED 和 REJECTED 均为不可逆终态。
 */
public enum SettlementReversalStatus {
    /**
     * PENDING APPROVAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PENDING_APPROVAL,
    /**
     * APPROVED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    APPROVED,
    REJECTED
}
