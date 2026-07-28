package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout URL token 状态枚举。
 */
@Getter
public enum PaymentCheckoutTokenStatusEnum {

    ACTIVE("ACTIVE", true),
    REVOKED("REVOKED", false),
    EXPIRED("EXPIRED", false);

    private final String code;

    private final boolean usable;

    PaymentCheckoutTokenStatusEnum(String code, boolean usable) {
        this.code = code;
        this.usable = usable;
    }
}
