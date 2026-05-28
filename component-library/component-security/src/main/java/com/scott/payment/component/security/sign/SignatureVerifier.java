package com.scott.payment.component.security.sign;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SignatureVerifier
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户请求签名验签接口
 * @status : create
 */
public interface SignatureVerifier {

    boolean verify(Map<String, String> parameters, String signature, String secret);
}

