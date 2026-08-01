package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 前端页面状态枚举。
 */
@Getter
public enum PaymentCheckoutPageStateEnum {

    /** 会话有效且允许付款人提交支付。 */
    PAYABLE("PAYABLE"),
    /** 付款人需要继续完成 3DS 验证。 */
    THREE_DS_REQUIRED("THREE_DS_REQUIRED"),
    /** 支付结果尚未确定，前端应按策略轮询。 */
    PROCESSING("PROCESSING"),
    /** 支付已成功，可展示成功结果。 */
    SUCCEEDED("SUCCEEDED"),
    /** 本次尝试失败但会话仍允许重试。 */
    FAILED_RETRYABLE("FAILED_RETRYABLE"),
    /** 会话已最终失败，不再允许重试。 */
    FAILED_FINAL("FAILED_FINAL"),
    /** 会话超过有效期。 */
    EXPIRED("EXPIRED"),
    /** 会话已取消。 */
    CANCELLED("CANCELLED"),
    /** 安全策略阻断会话访问或支付。 */
    BLOCKED("BLOCKED");

    /** 对浏览器响应使用的稳定页面状态编码。 */
    private final String code;

    PaymentCheckoutPageStateEnum(String code) {
        this.code = code;
    }
}
