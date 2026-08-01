package com.scott.payment.payment.mq;

import com.scott.payment.component.mq.constant.MqTag;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMqConstants
 * @date : 2026-07-14 21:45
 * @email : scott_x@163.com
 * @description : 收单交易 MQ 常量，位于 service-payment 消息层，集中声明交易事件消费者分组和消息标签。
 * @status : create
 */
public final class TransactionMqConstants {

    /**
     * 交易创建或终态事件标签。
     */
    public static final String TRANSACTION_CREATED_TAG = MqTag.TRANSACTION_CREATED;

    /**
     * 渠道回调推进交易终态事件标签。
     */
    public static final String TRANSACTION_CALLBACK_PROCESSED_TAG = MqTag.TRANSACTION_CALLBACK_PROCESSED;

    /**
     * 同步渠道结果或主动查询推进终态后的状态变更事件标签。
     */
    public static final String TRANSACTION_STATUS_CHANGED_TAG = MqTag.TRANSACTION_STATUS_CHANGED;

    private TransactionMqConstants() {
    }
}
