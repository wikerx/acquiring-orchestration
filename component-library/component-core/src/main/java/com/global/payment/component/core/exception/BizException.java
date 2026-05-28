package com.global.payment.component.core.exception;

public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

