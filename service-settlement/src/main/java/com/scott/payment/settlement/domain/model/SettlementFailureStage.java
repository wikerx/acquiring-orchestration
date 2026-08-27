package com.scott.payment.settlement.domain.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementFailureStage
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 结算批次自动处理的稳定失败阶段；用于补偿定位，不替代批次状态机。
 * @status : create
 */
public enum SettlementFailureStage {
    FACT_LOADING,
    RATE_LOCKING,
    RESULT_CALCULATION,
    LEDGER_POSTING,
    TRANSACTION_PROJECTION,
    EVENT_PUBLICATION
}
