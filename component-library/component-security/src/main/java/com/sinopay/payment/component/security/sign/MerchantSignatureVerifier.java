package com.sinopay.payment.component.security.sign;

import com.sinopay.payment.component.security.crypto.HmacSha256Signer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        if (signature == null || secret == null) {
            return false;
        }
        String expectedSignature = signer.sign(parameters, secret);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
