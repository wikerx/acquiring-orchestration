package com.scott.payment.openapi.security;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * OpenAPI 报文加密 RSA 密钥提供接口
 */
public interface OpenApiPayloadKeyProvider {

    /**
     * 根据商户号获取该商户独立的平台私钥，用于解密商户请求中的 AES 会话密钥。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 平台 RSA 私钥
     */
    PrivateKey getPlatformPrivateKey(String merchantId);

    /**
     * 根据商户号获取该商户独立的平台公钥，主要用于本地联调生成商户请求密文。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 平台 RSA 公钥
     */
    PublicKey getPlatformPublicKey(String merchantId);
}
