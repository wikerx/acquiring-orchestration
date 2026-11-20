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

    /** 返回失败修正明细号，供回滚后的独立失败记录事务定位。 */
    public String getCorrectionItemNo() {
        return correctionItemNo;
    }

    /** 提供失败时读取的版本，防止失败记录覆盖并发推进后的状态。 */
    public long getExpectedVersion() {
        return expectedVersion;
    }

    /** 提供失败时的重试次数，供条件更新退避状态时核对。 */
    public int getExpectedRetryCount() {
        return expectedRetryCount;
    }

    /** 返回受控失败码用于退避及告警，不携带敏感原始报文。 */
    public String getFailureCode() {
        return failureCode;
    }
}
