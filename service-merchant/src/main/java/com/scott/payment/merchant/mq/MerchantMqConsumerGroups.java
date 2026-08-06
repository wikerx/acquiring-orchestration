package com.scott.payment.merchant.mq;

/** service-merchant 独占的 RocketMQ 消费组。 */
public final class MerchantMqConsumerGroups {

    /** Merchant 业务邮件投递消费组。 */
    public static final String EMAIL_DELIVERY = "acquiring-merchant-email-delivery-consumer";

    private MerchantMqConsumerGroups() {
    }
}
