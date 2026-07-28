package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 安全决策枚举。
 */
@Getter
public enum PaymentCheckoutSecurityDecisionEnum {

    ALLOW("ALLOW"),
    BLOCK("BLOCK"),
    CHALLENGE("CHALLENGE"),
    LOG_ONLY("LOG_ONLY");

    private final String code;

    PaymentCheckoutSecurityDecisionEnum(String code) {
        this.code = code;
    }
}
