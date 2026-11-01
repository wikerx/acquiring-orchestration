package com.scott.payment.payment.domain.refund;

/**
 * 退款执行消息处理结果，用于区分首次执行、转主动查询和被状态机安全忽略。
 */
public enum RefundExecutionOutcomeEnum {
    /**
     * 本次消费成功取得执行权并向渠道发起退款。
     */
    EXECUTED,

    /**
     * 前次请求结果未知，本次消费只触发原渠道、原 MID 的主动查询。
     */
    QUERY_TRIGGERED,

    /**
     * 动作已被其他消费者处理或已进入终态，本次消息按幂等语义忽略。
     */
    IGNORED
}
