package com.scott.payment.data.mq;

/**
 * 集中定义 service-data 的 RocketMQ 消费组，确保同类异步事实只由该服务集群消费。
 */
public final class DataMqConsumerGroups {

    /**
     * Admin 操作日志消费组。
     */
    public static final String ADMIN_OPERATION_LOG = "acquiring-data-admin-operation-log-consumer";

    /**
     * Merchant 操作日志消费组。
     */
    public static final String MERCHANT_OPERATION_LOG = "acquiring-data-merchant-operation-log-consumer";

    /**
     * 风控评估审计消费组。
     */
    public static final String RISK_EVALUATION_AUDIT = "acquiring-data-risk-evaluation-audit-consumer";

    /**
     * 商户交易结果通知消费组。
     */
    public static final String MERCHANT_NOTIFICATION = "acquiring-data-merchant-notification-consumer";

    /**
     * OpenAPI 安全拦截审计消费组。
     */
    public static final String SECURITY_INTERCEPT_AUDIT = "acquiring-data-security-intercept-audit-consumer";

    /**
     * Admin 与 Merchant 登录审计消费组。
     */
    public static final String LOGIN_AUDIT = "acquiring-data-login-audit-consumer";

    /**
     * 收银台卡资料密文消费组。
     */
    public static final String CHECKOUT_CARD_VAULT = "acquiring-data-checkout-card-vault-consumer";

    /**
     * Payment 与 Settlement 实际汇率使用事实消费组。
     */
    public static final String EXCHANGE_RATE_USAGE = "acquiring-data-exchange-rate-usage-consumer";

    /**
     * 常量类不允许实例化。
     */
    private DataMqConsumerGroups() {
    }
}
