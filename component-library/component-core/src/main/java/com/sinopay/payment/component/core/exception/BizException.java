package com.sinopay.payment.component.core.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BizException
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 业务异常定义
 * @status : create
 */
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

