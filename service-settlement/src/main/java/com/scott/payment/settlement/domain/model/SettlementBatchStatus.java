package com.scott.payment.settlement.domain.model;

/**
 * 结算批次权威状态枚举；计算、资金入账、取消和独立冲正均通过受控状态与版本 CAS 单向迁移。
 */
public enum SettlementBatchStatus {
    /**
     * CREATED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CREATED,
    /**
     * CLAIMING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CLAIMING,
    /**
     * CLAIMED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CLAIMED,
    /**
     * RATE LOCKED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RATE_LOCKED,
    /**
     * CALCULATING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CALCULATING,
    /**
     * CALCULATED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CALCULATED,
    /**
     * POSTING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    POSTING,
    /**
     * POSTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    POSTED,
    /**
     * FAILED RETRYABLE 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    FAILED_RETRYABLE,
    /**
     * MANUAL REVIEW 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    MANUAL_REVIEW,
    /**
     * CANCELLED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CANCELLED,
    /**
     * REVERSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REVERSING,
    /**
     * REVERSED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REVERSED;

    /**
     * 判断批次是否仍处于候选认领窗口。
     *
     * @return CREATED 或 CLAIMING 返回 true
     */
    public boolean allowsCandidateClaim() {
        return this == CREATED || this == CLAIMING;
    }
}
