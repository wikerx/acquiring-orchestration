package com.scott.payment.component.mq.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqTopic
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 消息主题常量定义
 * @status : create
 */
public final class MqTopic {

    public static final String PAYMENT_EVENT = "payment-event";
    public static final String PAYOUT_EVENT = "payout-event";

    private MqTopic() {
    }
}

