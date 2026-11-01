package com.scott.payment.settlement.domain.model;

/**
 * 结算批次自动处理的稳定失败阶段；用于补偿定位，不替代批次状态机。
 */
public enum SettlementFailureStage {
    /**
     * FACT LOADING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    FACT_LOADING,
    /**
     * RATE LOCKING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RATE_LOCKING,
    /**
     * RESULT CALCULATION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RESULT_CALCULATION,
    /**
     * LEDGER POSTING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    LEDGER_POSTING,
    /**
     * TRANSACTION PROJECTION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    TRANSACTION_PROJECTION,
    EVENT_PUBLICATION
}
