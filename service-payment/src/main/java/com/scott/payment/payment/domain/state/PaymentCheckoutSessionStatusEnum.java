package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 会话状态枚举。
 */
@Getter
public enum PaymentCheckoutSessionStatusEnum {

    PAYABLE("PAYABLE", false),
    PAYING("PAYING", false),
    AUTHENTICATING("AUTHENTICATING", false),
    PROCESSING("PROCESSING", false),
    PAYABLE_FAILED_RETRYABLE("PAYABLE_FAILED_RETRYABLE", false),
    SUCCEEDED("SUCCEEDED", true),
    FAILED_FINAL("FAILED_FINAL", true),
    EXPIRED("EXPIRED", true),
    CANCELLED("CANCELLED", true),
    BLOCKED("BLOCKED", true);

    private final String code;

    private final boolean terminal;

    PaymentCheckoutSessionStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
