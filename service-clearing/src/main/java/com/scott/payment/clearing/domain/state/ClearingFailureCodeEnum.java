package com.scott.payment.clearing.domain.state;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingFailureCodeEnum
 * @date : 2026-08-26 08:28
 * @email : scott_x@163.com
 * @description : 清分失败分类和重试属性，避免异常处理使用散落字符串或把确定性资金风险无限重试。
 * @status : create
 */
public enum ClearingFailureCodeEnum {
    TRANSACTION_NOT_FOUND(true),
    TRANSACTION_NOT_TERMINAL(false),
    TRANSACTION_VERSION_CONFLICT(true),
    FEE_SNAPSHOT_MISSING(true),
    FEE_SNAPSHOT_HASH_MISMATCH(false),
    FEE_VERSION_NOT_FOUND(true),
    FEE_VERSION_NOT_IMMUTABLE(false),
    FEE_RULE_NOT_CONFIGURED(false),
    FEE_RULE_AMBIGUOUS(false),
    AMOUNT_INVALID(false),
    FEE_COMPONENT_CURRENCY_INVALID(false),
    SOURCE_CLEARING_PENDING(true),
    SOURCE_CLEARING_NOT_FOUND(true),
    SOURCE_SETTLEMENT_PENDING(true),
    TIER_ACCUMULATOR_CONFLICT(true),
    RESERVE_SOURCE_NOT_FOUND(true),
    RESERVE_RETURN_EXCEEDED(false),
    RESERVE_STATE_CONFLICT(true),
    CLEARING_COMPENSATION_DUE(true),
    CLEARING_MANUAL_RETRY(true),
    CLEARING_CAS_CONFLICT(true),
    CLEARING_PERSISTENCE_ERROR(true),
    CLEARING_RETRY_EXHAUSTED(false);

    private final boolean retryable;

    ClearingFailureCodeEnum(boolean retryable) {
        this.retryable = retryable;
    }

    /** @return 是否允许进入有上限的业务延时重试 */
    public boolean isRetryable() {
        return retryable;
    }
}
