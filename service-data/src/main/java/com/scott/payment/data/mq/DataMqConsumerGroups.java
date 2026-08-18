package com.scott.payment.data.mq;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMqConsumerGroups
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : service-data RocketMQ 消费组常量，确保同一类异步事实只由该服务集群消费
 * @status : create
 */
public final class DataMqConsumerGroups {

    /** Admin 操作日志消费组。 */
    public static final String ADMIN_OPERATION_LOG = "acquiring-data-admin-operation-log-consumer";

    /** Merchant 操作日志消费组。 */
    public static final String MERCHANT_OPERATION_LOG = "acquiring-data-merchant-operation-log-consumer";

    /** 风控评估审计消费组。 */
    public static final String RISK_EVALUATION_AUDIT = "acquiring-data-risk-evaluation-audit-consumer";

    /** 商户交易结果通知消费组。 */
    public static final String MERCHANT_NOTIFICATION = "acquiring-data-merchant-notification-consumer";

    /** OpenAPI 安全拦截审计消费组。 */
    public static final String SECURITY_INTERCEPT_AUDIT = "acquiring-data-security-intercept-audit-consumer";

    /** Admin 与 Merchant 登录审计消费组。 */
    public static final String LOGIN_AUDIT = "acquiring-data-login-audit-consumer";

    /** 收银台卡资料密文消费组。 */
    public static final String CHECKOUT_CARD_VAULT = "acquiring-data-checkout-card-vault-consumer";

    private DataMqConsumerGroups() {
    }
}
