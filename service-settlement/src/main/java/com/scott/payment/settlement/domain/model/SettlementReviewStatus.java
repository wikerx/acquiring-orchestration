package com.scott.payment.settlement.domain.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewStatus
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 定义结算预审单生命周期；除 PENDING_APPROVAL 外的审批、拒绝、取消和过期状态均不可逆。
 * @status : create
 */
public enum SettlementReviewStatus {
    /**
     * PENDING APPROVAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PENDING_APPROVAL,
    /**
     * APPROVED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    APPROVED,
    /**
     * REJECTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REJECTED,
    /**
     * CANCELLED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CANCELLED,
    EXPIRED
}
