package com.scott.payment.settlement.exception;

/**
 * 历史标签本金投影修正失败身份；主事务回滚后由独立事务记录退避状态。
 */
public class SettlementPrincipalCorrectionProjectionException extends RuntimeException {

    private final String correctionItemNo;
    private final long expectedVersion;
    private final int expectedRetryCount;
    private final String failureCode;

    public SettlementPrincipalCorrectionProjectionException(String correctionItemNo,
                                                             long expectedVersion,
                                                             int expectedRetryCount,
                                                             String failureCode,
                                                             Throwable cause) {
        super("settlement principal correction projection failed", cause);
        this.correctionItemNo = correctionItemNo;
        this.expectedVersion = expectedVersion;
        this.expectedRetryCount = expectedRetryCount;
        this.failureCode = failureCode;
    }

    public String getCorrectionItemNo() {
        return correctionItemNo;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public int getExpectedRetryCount() {
        return expectedRetryCount;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
