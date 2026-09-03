package com.scott.payment.admin.mq;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMqConsumerGroups
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : service-admin 独占的 RocketMQ 消费组。
 * @status : create
 */
public final class AdminMqConsumerGroups {

    /** Admin 业务邮件投递消费组。 */
    public static final String EMAIL_DELIVERY = "acquiring-admin-email-delivery-consumer";

    private AdminMqConsumerGroups() {
    }
}
