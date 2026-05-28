package com.sinopay.payment.component.security.crypto;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HmacSha256Signer
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : HMAC SHA256 签名工具
 * @status : create
 */
public class HmacSha256Signer {

    public String sign(Map<String, String> parameters, String secret) {
        TreeMap<String, String> sortedParameters = new TreeMap<>(parameters);
        return Integer.toHexString((sortedParameters.toString() + secret).hashCode());
    }
}

