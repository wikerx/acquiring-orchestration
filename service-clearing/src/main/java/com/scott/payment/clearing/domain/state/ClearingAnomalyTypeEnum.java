package com.scott.payment.clearing.domain.state;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingAnomalyTypeEnum
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分异常案件分类；分类值同时作为低基数指标标签。
 * @status : update
 */
public enum ClearingAnomalyTypeEnum {
    /**
     * CONTROLLED FAILURE 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CONTROLLED_FAILURE,
    /**
     * FINANCIAL MISMATCH 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    FINANCIAL_MISMATCH,
    /**
     * PROJECTION MISMATCH 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PROJECTION_MISMATCH,
    MANUAL_REVIEW
}
