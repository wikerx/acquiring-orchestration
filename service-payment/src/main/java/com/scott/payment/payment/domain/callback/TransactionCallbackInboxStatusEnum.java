package com.scott.payment.payment.domain.callback;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxStatusEnum
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox 状态枚举，位于 service-payment 领域层，约束受理、抢占、重试、完成和封顶关闭的单向流转。
 * @status : create
 */
public enum TransactionCallbackInboxStatusEnum {
    RECEIVED,
    PROCESSING,
    RETRY,
    PROCESSED,
    CLOSED
}
