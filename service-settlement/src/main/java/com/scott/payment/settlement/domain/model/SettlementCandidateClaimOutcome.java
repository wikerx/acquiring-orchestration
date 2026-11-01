package com.scott.payment.settlement.domain.model;

/**
 * 候选认领命令结果，区分首次成功和同一批次安全重试。
 */
public enum SettlementCandidateClaimOutcome {
    /**
     * CLAIMED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CLAIMED,
    ALREADY_CLAIMED
}
