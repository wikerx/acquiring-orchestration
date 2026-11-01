package com.scott.payment.risk.mq;

/**
 * Risk 领域仍负责消费的支付事件 Tag 与限额生命周期消费组常量
 */
public final class RiskMqConstants {

    /**
     * 支付交易创建事件 Tag，用于驱动累计限额生命周期。
     */
    public static final String PAYMENT_TRANSACTION_CREATED_TAG = "TRANSACTION_CREATED";

    public static final String PAYMENT_TRANSACTION_CALLBACK_PROCESSED_TAG =
            "TRANSACTION_CALLBACK_PROCESSED";

    public static final String PAYMENT_TRANSACTION_STATUS_CHANGED_TAG =
            "TRANSACTION_STATUS_CHANGED";

    public static final String MERCHANT_LIMIT_LIFECYCLE_CONSUMER_GROUP =
            "acquiring-risk-merchant-limit-lifecycle-consumer";

    /**
     * 频控成功名额生命周期使用独立消费进度，避免与金额限额相互阻塞。
     */
    public static final String FREQUENCY_SUCCESS_LIFECYCLE_CONSUMER_GROUP =
            "acquiring-risk-frequency-success-lifecycle-consumer";

    /**
     * 工具常量类不允许实例化。
     */
    private RiskMqConstants() {
    }
}
