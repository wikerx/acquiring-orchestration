package com.scott.payment.payment.domain.refund;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundExecutionOutcomeEnum
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 退款执行消息处理结果，用于区分首次执行、转主动查询和被状态机安全忽略。
 * @status : create
 */
public enum RefundExecutionOutcomeEnum {
    /**
     * EXECUTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    EXECUTED,
    /**
     * QUERY TRIGGERED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    QUERY_TRIGGERED,
    IGNORED
}
