package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 会话状态枚举。
 */
@Getter
public enum PaymentCheckoutSessionStatusEnum {

    /** 会话有效且允许创建支付尝试。 */
    PAYABLE("PAYABLE", false),
    /** 付款人已提交支付，正在处理本次尝试。 */
    PAYING("PAYING", false),
    /** 正在执行或等待 3DS 认证。 */
    AUTHENTICATING("AUTHENTICATING", false),
    /** 渠道结果尚未确定。 */
    PROCESSING("PROCESSING", false),
    /** 最近尝试失败，但会话仍允许重试。 */
    PAYABLE_FAILED_RETRYABLE("PAYABLE_FAILED_RETRYABLE", false),
    /** 会话支付成功终态。 */
    SUCCEEDED("SUCCEEDED", true),
    /** 会话最终失败终态。 */
    FAILED_FINAL("FAILED_FINAL", true),
    /** 会话过期终态。 */
    EXPIRED("EXPIRED", true),
    /** 会话取消终态。 */
    CANCELLED("CANCELLED", true),
    /** 会话被安全策略阻断的终态。 */
    BLOCKED("BLOCKED", true);

    /** 持久化和内部协议使用的稳定会话状态编码。 */
    private final String code;

    /** 是否禁止继续创建支付尝试或覆盖当前状态。 */
    private final boolean terminal;

    PaymentCheckoutSessionStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
