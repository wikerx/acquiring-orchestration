package com.scott.payment.risk.mq;

/**
 * 风控 MQ 常量。
 */
public final class RiskMqConstants {

    /** 风控评估审计消息 Tag。 */
    public static final String RISK_EVALUATION_AUDIT_TAG = "risk-evaluation-audit";

    /** 风控审计落库消费者组；同一环境内所有实例共享该组。 */
    public static final String RISK_EVALUATION_AUDIT_CONSUMER_GROUP = "acquiring-risk-evaluation-audit-consumer-dev";

    /** 支付交易创建事件 Tag，用于驱动累计限额生命周期。 */
    public static final String PAYMENT_TRANSACTION_CREATED_TAG = "TRANSACTION_CREATED";

    public static final String PAYMENT_TRANSACTION_CALLBACK_PROCESSED_TAG =
            "TRANSACTION_CALLBACK_PROCESSED";

    public static final String PAYMENT_TRANSACTION_STATUS_CHANGED_TAG =
            "TRANSACTION_STATUS_CHANGED";

    public static final String MERCHANT_LIMIT_LIFECYCLE_CONSUMER_GROUP =
            "acquiring-risk-merchant-limit-lifecycle-consumer";

    private RiskMqConstants() {
    }
}
