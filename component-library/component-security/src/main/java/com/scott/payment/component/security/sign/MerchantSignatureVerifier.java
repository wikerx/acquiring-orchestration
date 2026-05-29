package com.scott.payment.component.security.sign;

import com.scott.payment.component.security.crypto.HmacSha256Signer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSignatureVerifier
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户普通参数签名验签实现，OpenAPI 支付接口默认不使用该类做 JWT 鉴权
 * @status : create
 */
public class MerchantSignatureVerifier implements SignatureVerifier {

    /**
     * HMAC-SHA256 签名器，负责生成期望签名，当前类只负责常量时间比较和验签编排。
     * <p>
     * OpenAPI 支付接口已经切换为标准 JWT HS256；该字段仅服务旧式参数签名场景。
     */
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
