package com.scott.payment.settlement.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementProjectionProcessingException
 * @date : 2026-08-26 21:00
 * @email : scott_x@163.com
 * @description : 结算交易投影事务失败身份；携带回滚前的任务版本和重试次数，供外层独立事务安全记录退避。
 * @status : create
 */
public class SettlementProjectionProcessingException extends RuntimeException {

    /** 回滚后重新定位投影任务的全局幂等号。 */
    private final String taskNo;
    /** 回滚后任务应恢复的 CAS 版本。 */
    private final long expectedVersion;
    /** 回滚后任务应恢复的重试次数。 */
    private final int expectedRetryCount;
    /** 可用于告警聚合且不包含敏感正文的稳定失败码。 */
    private final String failureCode;

    /**
     * 构造投影失败身份；原始异常只保留在服务端异常链中。
     *
     * @param taskNo 投影任务号
     * @param expectedVersion 事务回滚后的期望版本
     * @param expectedRetryCount 事务回滚后的期望重试次数
     * @param failureCode 稳定失败码
     * @param cause 触发事务回滚的原始异常
     */
    public SettlementProjectionProcessingException(String taskNo,
                                                   long expectedVersion,
                                                   int expectedRetryCount,
                                                   String failureCode,
                                                   Throwable cause) {
        super("settlement transaction projection failed", cause);
        this.taskNo = taskNo;
        this.expectedVersion = expectedVersion;
        this.expectedRetryCount = expectedRetryCount;
        this.failureCode = failureCode;
    }

    /** @return 投影任务全局幂等号 */
    public String getTaskNo() {
        return taskNo;
    }

    /** @return 回滚后的期望任务版本 */
    public long getExpectedVersion() {
        return expectedVersion;
    }

    /** @return 回滚后的期望重试次数 */
    public int getExpectedRetryCount() {
        return expectedRetryCount;
    }

    /** @return 稳定失败码 */
    public String getFailureCode() {
        return failureCode;
    }
}
