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

    /**
     * 使用调用方约定的规范参数序列和密钥校验请求签名。
     *
     * @param parameters 待验签参数，不得包含调用方约定排除的签名字段
     * @param signature 请求携带的签名值
     * @param secret 验签密钥，属于敏感信息，禁止记录日志
     * @return 签名匹配时返回 true，否则返回 false
     */
    boolean verify(Map<String, String> parameters, String signature, String secret);
}
