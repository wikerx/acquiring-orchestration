package com.scott.payment.admin.mq;

/** service-admin 独占的 RocketMQ 消费组。 */
public final class AdminMqConsumerGroups {

    /** Admin 业务邮件投递消费组。 */
    public static final String EMAIL_DELIVERY = "acquiring-admin-email-delivery-consumer";

    private AdminMqConsumerGroups() {
    }
}
