package com.scott.payment.settlement.domain.model;

/**
 * 结算批次业务类型；只有冲正引用原批次，保证金调整引用原 HOLD 清分事实。
 */
public enum SettlementBatchType {
    /**
     * REGULAR 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REGULAR(false),
    /**
     * RESERVE RELEASE 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RESERVE_RELEASE(false),
    /**
     * REVERSAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REVERSAL(true),
    /**
     * ADJUSTMENT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    ADJUSTMENT(false);

    /**
     * 是否必须关联原结算批次；冲正批次为 true，普通结算批次为 false。
     */
    private final boolean originalBatchRequired;

    SettlementBatchType(boolean originalBatchRequired) {
        this.originalBatchRequired = originalBatchRequired;
    }

    /**
     * 判断当前批次类型是否必须引用原批次。
     *
     * @return 冲正批次返回 true
     */
    public boolean isOriginalBatchRequired() {
        return originalBatchRequired;
    }
}
