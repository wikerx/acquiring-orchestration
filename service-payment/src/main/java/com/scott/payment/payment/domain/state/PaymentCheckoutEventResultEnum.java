package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 事件处理结果枚举。
 */
@Getter
public enum PaymentCheckoutEventResultEnum {

    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    IGNORED("IGNORED");

    private final String code;

    PaymentCheckoutEventResultEnum(String code) {
        this.code = code;
    }
}
