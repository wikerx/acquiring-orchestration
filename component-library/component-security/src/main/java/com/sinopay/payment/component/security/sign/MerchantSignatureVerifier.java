package com.sinopay.payment.component.security.sign;

import com.sinopay.payment.component.security.crypto.HmacSha256Signer;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSignatureVerifier
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户请求签名验签实现
 * @status : create
 */
public class MerchantSignatureVerifier implements SignatureVerifier {

    private final HmacSha256Signer signer = new HmacSha256Signer();

    @Override
    public boolean verify(Map<String, String> parameters, String signature, String secret) {
        return signer.sign(parameters, secret).equals(signature);
    }
}

