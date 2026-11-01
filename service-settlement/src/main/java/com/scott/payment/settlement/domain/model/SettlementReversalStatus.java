package com.scott.payment.settlement.domain.model;

/**
 * 定义独立结算冲正申请的 Maker-Checker 状态；APPROVED 和 REJECTED 均为不可逆终态。
 */
public enum SettlementReversalStatus {
    /**
     * PENDING APPROVAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PENDING_APPROVAL,
    /**
     * APPROVED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    APPROVED,
    REJECTED
}
