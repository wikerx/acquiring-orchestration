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

    /**
     * 收单普通消息主题，用于商户通知等不要求交易级顺序的兼容消息。
     */
    public static final String PAYMENT_EVENT = "payment-event";

    /**
     * 收单交易生命周期专用 RocketMQ 5.x FIFO Topic，同一 operationId 的事件保持顺序。
     */
    public static final String PAYMENT_TRANSACTION_FIFO = "acquiring_payment_transaction_fifo_topic";

    /**
     * 清分异常重试专用 RocketMQ 5.x Delay Topic，不与 FIFO 交易事件 Topic 混用。
     */
    public static final String PAYMENT_CLEARING_DELAY = "acquiring_payment_clearing_delay_topic";

    /**
     * 代付交易事件主题，用于代付创建、状态变更、通知和对账相关异步消息。
     */
    public static final String PAYOUT_EVENT = "payout-event";

    /**
     * 后台管理系统操作日志 Topic。
     */
    public static final String ADMIN_OPERATION_LOG = "acquiring_admin_operation_log_topic";

    /**
     * 商户管理系统操作日志 Topic。
     */
    public static final String MERCHANT_OPERATION_LOG = "acquiring_merchant_operation_log_topic";

    /**
     * 风控评估审计 Topic。
     */
    public static final String RISK_EVALUATION_AUDIT = "acquiring_risk_evaluation_audit_topic";

    /**
     * OpenAPI 安全拦截审计 Topic。
     */
    public static final String SECURITY_INTERCEPT_AUDIT = "acquiring_security_intercept_audit_topic";

    /**
     * Admin 与 Merchant 登录审计 Topic。
     */
    public static final String LOGIN_AUDIT = "acquiring_login_audit_topic";

    /**
     * Admin 与 Merchant 业务邮件异步投递 Topic。
     */
    public static final String EMAIL_DELIVERY = "acquiring_email_delivery_topic";

    /**
     * 收银台卡资料库传输 Topic，只允许承载由 service-data 公钥加密且不含 CVV 的信封。
     */
    public static final String CHECKOUT_CARD_VAULT = "acquiring_checkout_card_vault_topic";

    /** 跨服务缓存 generation 变更 Topic，只承载命名空间和发布凭证。 */
    public static final String CACHE_INVALIDATION = "acquiring_cache_invalidation_topic";

    private MqTopic() {
    }
}
