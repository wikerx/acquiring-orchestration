package com.scott.payment.payment.mq.message;

import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventMessage
 * @date : 2026-07-14 21:46
 * @email : scott_x@163.com
 * @description : 收单交易事件消息体，位于 service-payment 消息层，携带交易分表时间和平台标识供通知、对账等异步消费者定位数据。
 * @status : create
 */
public class TransactionEventMessage extends PaymentTransactionEventMessage {
}
