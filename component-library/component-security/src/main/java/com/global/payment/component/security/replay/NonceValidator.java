package com.global.payment.component.security.replay;

public interface NonceValidator {

    boolean validate(String nonce, long timestamp);
}

