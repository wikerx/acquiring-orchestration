package com.sinopay.payment.component.security.replay;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NonceValidator
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 请求 Nonce 防重放校验接口
 * @status : create
 */
public interface NonceValidator {

    boolean validate(String nonce, long timestamp);
}

