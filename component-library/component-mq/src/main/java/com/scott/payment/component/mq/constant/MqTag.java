package com.scott.payment.component.mq.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqTag
 * @date : 2026-08-01 14:50
 * @email : scott_x@163.com
 * @description : 跨生产者与消费者共享的 RocketMQ Tag 常量，防止服务间使用不一致的路由标签
 * @status : create
 */
public final class MqTag {

    /** 交易创建事件标签。 */
    public static final String TRANSACTION_CREATED = "TRANSACTION_CREATED";

    /** 渠道回调完成并推进交易状态的事件标签。 */
    public static final String TRANSACTION_CALLBACK_PROCESSED = "TRANSACTION_CALLBACK_PROCESSED";

    /** 同步渠道结果或主动查询推进交易状态的事件标签。 */
    public static final String TRANSACTION_STATUS_CHANGED = "TRANSACTION_STATUS_CHANGED";

    /** 风控评估审计消息标签。 */
    public static final String RISK_EVALUATION_AUDIT = "risk-evaluation-audit";

    /** OpenAPI 安全拦截审计消息标签。 */
    public static final String SECURITY_INTERCEPT_AUDIT = "security-intercept-audit";

    /** 登录成功与失败审计消息标签。 */
    public static final String LOGIN_AUDIT = "login-audit";

    /** Admin 业务邮件投递标签。 */
    public static final String ADMIN_EMAIL_DELIVERY = "admin-email-delivery";

    /** Merchant 业务邮件投递标签。 */
    public static final String MERCHANT_EMAIL_DELIVERY = "merchant-email-delivery";

    /** 管理后台人工重发商户终态回调标签。 */
    public static final String MERCHANT_NOTIFICATION_RETRY_REQUESTED = "MERCHANT_NOTIFICATION_RETRY_REQUESTED";

    private MqTag() {
    }
}
