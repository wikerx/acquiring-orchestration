package com.scott.payment.settlement.domain.model;

/**
 * 清分修订级结算候选状态；候选只能从 READY 通过版本 CAS 进入 CLAIMED。
 */
public enum SettlementCandidateStatus {
    /**
     * READY 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    READY,
    /**
     * REPLAY HOLD 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REPLAY_HOLD,
    /**
     * REVIEW LOCKED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REVIEW_LOCKED,
    /**
     * SUPERSEDED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    SUPERSEDED,
    /**
     * CLAIMED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CLAIMED,
    /**
     * POSTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    POSTED,
    /**
     * MANUAL REVIEW 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    MANUAL_REVIEW,
    CANCELLED
}
