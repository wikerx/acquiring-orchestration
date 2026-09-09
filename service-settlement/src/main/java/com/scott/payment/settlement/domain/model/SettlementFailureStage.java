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
    /**
     * FACT LOADING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    FACT_LOADING,
    /**
     * RATE LOCKING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RATE_LOCKING,
    /**
     * RESULT CALCULATION 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RESULT_CALCULATION,
    /**
     * LEDGER POSTING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    LEDGER_POSTING,
    /**
     * TRANSACTION PROJECTION 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    TRANSACTION_PROJECTION,
    EVENT_PUBLICATION
}
