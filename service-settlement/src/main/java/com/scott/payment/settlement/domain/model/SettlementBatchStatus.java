package com.scott.payment.settlement.domain.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchStatus
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次权威状态枚举；计算、资金入账、取消和独立冲正均通过受控状态与版本 CAS 单向迁移。
 * @status : create
 */
public enum SettlementBatchStatus {
    CREATED,
    CLAIMING,
    CLAIMED,
    RATE_LOCKED,
    CALCULATING,
    CALCULATED,
    POSTING,
    POSTED,
    FAILED_RETRYABLE,
    MANUAL_REVIEW,
    CANCELLED,
    REVERSING,
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
