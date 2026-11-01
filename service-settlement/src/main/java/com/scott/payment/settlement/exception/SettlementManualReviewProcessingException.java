package com.scott.payment.settlement.exception;

import com.scott.payment.component.core.exception.ServiceException;

/**
 * 手动结算异步任务的稳定失败码和可重试属性。
 */
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

    /**
     * 查询失败编码；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public String getFailureCode() {
        return failureCode;
    }

    /**
     * 判断 is retryable 条件是否成立，用于控制 Settlement Manual Review Processing Exception 的后续分支。
     * <p>
     * 纯判断操作，不修改业务状态。
     * </p>
     * @return 条件满足时返回 true，否则返回 false
     */
    public boolean isRetryable() {
        return retryable;
    }
}
