package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * Hosted Checkout 内部处理阶段枚举。
 */
@Getter
public enum PaymentCheckoutProcessStageEnum {

    SESSION_CREATED("SESSION_CREATED"),
    WAITING_PAYER("WAITING_PAYER"),
    CARD_VALIDATE("CARD_VALIDATE"),
    CARD_SUBMITTED("CARD_SUBMITTED"),
    INITIATE_3DS("INITIATE_3DS"),
    AUTHENTICATE_PAYER("AUTHENTICATE_PAYER"),
    WAITING_3DS("WAITING_3DS"),
    SUBMIT_CHANNEL("SUBMIT_CHANNEL"),
    WAITING_CHANNEL("WAITING_CHANNEL"),
    RESULT_RENDERED("RESULT_RENDERED");

    private final String code;

    PaymentCheckoutProcessStageEnum(String code) {
        this.code = code;
    }
}
