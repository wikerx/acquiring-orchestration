package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 前端页面状态枚举。
 */
@Getter
public enum PaymentCheckoutPageStateEnum {

    PAYABLE("PAYABLE"),
    THREE_DS_REQUIRED("THREE_DS_REQUIRED"),
    PROCESSING("PROCESSING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED_RETRYABLE("FAILED_RETRYABLE"),
    FAILED_FINAL("FAILED_FINAL"),
    EXPIRED("EXPIRED"),
    CANCELLED("CANCELLED"),
    BLOCKED("BLOCKED");

    private final String code;

    PaymentCheckoutPageStateEnum(String code) {
        this.code = code;
    }
}
