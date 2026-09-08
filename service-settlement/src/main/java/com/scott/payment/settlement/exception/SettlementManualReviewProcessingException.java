package com.scott.payment.settlement.exception;

import com.scott.payment.component.core.exception.ServiceException;

/** 手动结算异步任务的稳定失败码和可重试属性。 */
public class SettlementManualReviewProcessingException extends ServiceException {

    private final String failureCode;
    private final boolean retryable;

    public SettlementManualReviewProcessingException(String failureCode,
                                                     boolean retryable,
                                                     String message) {
        super(failureCode, message);
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
