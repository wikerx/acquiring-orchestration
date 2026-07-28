package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 支付尝试状态枚举。
 */
@Getter
public enum PaymentCheckoutAttemptStatusEnum {

    INIT("INIT", false),
    CARD_SUBMITTED("CARD_SUBMITTED", false),
    THREE_DS_INITIATED("THREE_DS_INITIATED", false),
    THREE_DS_REQUIRED("THREE_DS_REQUIRED", false),
    THREE_DS_RETURNED("THREE_DS_RETURNED", false),
    THREE_DS_PASSED("THREE_DS_PASSED", false),
    THREE_DS_FAILED("THREE_DS_FAILED", true),
    CHANNEL_SUBMITTED("CHANNEL_SUBMITTED", false),
    SUCCEEDED("SUCCEEDED", true),
    FAILED("FAILED", true),
    PROCESSING("PROCESSING", false),
    ABANDONED("ABANDONED", true);

    private final String code;

    private final boolean terminal;

    PaymentCheckoutAttemptStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
