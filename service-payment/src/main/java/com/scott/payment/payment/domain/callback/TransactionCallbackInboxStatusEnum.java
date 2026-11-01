package com.scott.payment.payment.domain.callback;

/**
 * 渠道回调 Inbox 状态枚举，位于 service-payment 领域层，约束受理、抢占、重试、完成和封顶关闭的单向流转。
 */
public enum TransactionCallbackInboxStatusEnum {
    /**
     * RECEIVED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RECEIVED,
    /**
     * PROCESSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PROCESSING,
    /**
     * RETRY 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RETRY,
    /**
     * PROCESSED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PROCESSED,
    CLOSED
}
