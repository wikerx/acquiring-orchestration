package com.scott.payment.settlement.domain.model;

/**
 * 定义结算预审单生命周期；除 PENDING_APPROVAL 外的审批、拒绝、取消和过期状态均不可逆。
 */
public enum SettlementReviewStatus {
    /**
     * PENDING APPROVAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PENDING_APPROVAL,
    /**
     * APPROVED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    APPROVED,
    /**
     * REJECTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REJECTED,
    /**
     * CANCELLED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CANCELLED,
    EXPIRED
}
