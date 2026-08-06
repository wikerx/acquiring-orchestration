package com.scott.payment.payment.domain.refund;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundExecutionOutcomeEnum
 * @date : 2026-08-06 00:00
 * @description : 退款执行消息处理结果，用于区分首次执行、转主动查询和被状态机安全忽略。
 * @status : create
 */
public enum RefundExecutionOutcomeEnum {
    EXECUTED,
    QUERY_TRIGGERED,
    IGNORED
}
