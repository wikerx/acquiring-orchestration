package com.scott.payment.settlement.exception;

import com.scott.payment.settlement.domain.model.SettlementFailureStage;

/**
 * 结算批次受控失败，携带稳定错误码、失败阶段和是否允许数据库补偿重试。
 */
public class SettlementProcessingException extends RuntimeException {

    /**
     * 结算失败阶段，用于确定补偿入口并防止跨阶段重复执行。
     */
    private final SettlementFailureStage stage;
    /**
     * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
     */
    private final String failureCode;
    /**
     * 失败是否允许重试；仅瞬时依赖故障可重试，业务校验和状态冲突不可重试。
     */
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

    /**
     * @return 失败阶段
     */
    public SettlementFailureStage getStage() {
        return stage;
    }

    /**
     * @return 稳定失败码
     */
    public String getFailureCode() {
        return failureCode;
    }

    /**
     * @return 是否允许自动重试
     */
    public boolean isRetryable() {
        return retryable;
    }
}
