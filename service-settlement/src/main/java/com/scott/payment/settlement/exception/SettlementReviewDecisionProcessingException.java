package com.scott.payment.settlement.exception;

/** 大批量预审异步决策的稳定失败码和可重试标记。 */
public class SettlementReviewDecisionProcessingException extends RuntimeException {

    private final String failureCode;
    private final boolean retryable;

    public SettlementReviewDecisionProcessingException(String failureCode,
                                                       boolean retryable,
                                                       String message) {
        super(message);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
