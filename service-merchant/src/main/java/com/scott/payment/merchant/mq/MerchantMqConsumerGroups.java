package com.scott.payment.merchant.mq;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantMqConsumerGroups
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : service-merchant 独占的 RocketMQ 消费组。
 * @status : create
 */
public final class MerchantMqConsumerGroups {

    /** Merchant 业务邮件投递消费组。 */
    public static final String EMAIL_DELIVERY = "acquiring-merchant-email-delivery-consumer";

    private MerchantMqConsumerGroups() {
    }
}
