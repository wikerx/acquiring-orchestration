package com.scott.payment.settlement.exception;

import com.scott.payment.settlement.domain.model.SettlementFailureStage;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementProcessingException
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 结算批次受控失败，携带稳定错误码、失败阶段和是否允许数据库补偿重试。
 * @status : create
 */
public class SettlementProcessingException extends RuntimeException {

    private final SettlementFailureStage stage;
    private final String failureCode;
    private final boolean retryable;

    /**
     * 创建受控结算异常。
     *
     * @param stage 失败阶段
     * @param failureCode 稳定失败码
     * @param retryable 是否允许自动重试
     * @param message 非敏感失败摘要
     */
    public SettlementProcessingException(SettlementFailureStage stage,
                                         String failureCode,
                                         boolean retryable,
                                         String message) {
        super(message);
        this.stage = stage;
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    /** @return 失败阶段 */
    public SettlementFailureStage getStage() {
        return stage;
    }

    /** @return 稳定失败码 */
    public String getFailureCode() {
        return failureCode;
    }

    /** @return 是否允许自动重试 */
    public boolean isRetryable() {
        return retryable;
    }
}
