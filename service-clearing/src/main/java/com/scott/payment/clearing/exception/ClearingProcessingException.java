package com.scott.payment.clearing.exception;

import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;

import java.util.Objects;

/**
 * 清分编排的受控失败，携带稳定失败码供状态机决定重试、等待源交易或人工复核。
 */
public class ClearingProcessingException extends RuntimeException {

    /**
     * 决定清分状态迁移、自动重试和异常案件分类的稳定失败码。
     */
    private final ClearingFailureCodeEnum failureCode;

    /**
     * 创建不包含敏感上下文的清分失败。
     *
     * @param failureCode 稳定失败分类
     * @param message 可写入失败摘要的非敏感说明
     */
    public ClearingProcessingException(ClearingFailureCodeEnum failureCode, String message) {
        super(message);
        this.failureCode = Objects.requireNonNull(failureCode, "clearing failure code is required");
    }

    /**
     * 读取供状态机决策的稳定清分失败码。
     *
     * @return 清分状态机使用的稳定失败码
     */
    public ClearingFailureCodeEnum getFailureCode() {
        return failureCode;
    }
}
