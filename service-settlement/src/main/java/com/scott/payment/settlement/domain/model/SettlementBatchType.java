package com.scott.payment.settlement.domain.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchType
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次业务类型；冲正和调整必须引用原批次，常规及保证金释放批次不得引用。
 * @status : create
 */
public enum SettlementBatchType {
    REGULAR(false),
    RESERVE_RELEASE(false),
    REVERSAL(true),
    ADJUSTMENT(true);

    private final boolean originalBatchRequired;

    SettlementBatchType(boolean originalBatchRequired) {
        this.originalBatchRequired = originalBatchRequired;
    }

    /**
     * 判断当前批次类型是否必须引用原批次。
     *
     * @return 冲正或调整批次返回 true
     */
    public boolean isOriginalBatchRequired() {
        return originalBatchRequired;
    }
}
