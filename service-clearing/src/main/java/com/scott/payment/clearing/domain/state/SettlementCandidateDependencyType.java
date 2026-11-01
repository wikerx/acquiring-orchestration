package com.scott.payment.clearing.domain.state;

/**
 * 结算候选依赖类型。
 *
 * <p>类型表达后续资金事实必须等待的源候选结果，持久化值与
 * {@code settlement_candidate_dependency.dependency_type} 约束保持一致。</p>
 */
public enum SettlementCandidateDependencyType {

    /**
     * 源交易本金资金结果必须先完成入账。
     */
    SOURCE_FINANCIAL_POSTED,

    /**
     * 源交易保证金扣留结果必须先完成入账。
     */
    RESERVE_HOLD_POSTED,

    /**
     * 源交易费用结果必须先完成入账。
     */
    SOURCE_FEE_RESULT_POSTED
}
