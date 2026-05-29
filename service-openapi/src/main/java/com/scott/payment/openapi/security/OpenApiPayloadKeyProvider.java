package com.scott.payment.openapi.security;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayloadKeyProvider
 * @date : 2026-05-29 21:45
 * @email : scott_x@163.com
 * @description : OpenAPI 报文加密 RSA 密钥提供接口
 * @status : create
 */
public interface OpenApiPayloadKeyProvider {

    /**
     * 根据密钥编号获取支付平台私钥，用于解密商户请求中的 AES 会话密钥。
     *
     * @param keyId 密文报文 header 中的密钥编号
     * @return 平台 RSA 私钥
     */
    PrivateKey getPlatformPrivateKey(String keyId);

    /**
     * 根据密钥编号获取支付平台公钥，主要用于本地联调生成商户请求密文。
     *
     * @param keyId 密钥编号
     * @return 平台 RSA 公钥
     */
    PublicKey getPlatformPublicKey(String keyId);
}
