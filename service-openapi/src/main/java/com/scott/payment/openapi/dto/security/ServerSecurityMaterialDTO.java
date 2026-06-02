package com.scott.payment.openapi.dto.security;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ServerSecurityMaterialDTO
 * @date : 2026-05-30 09:20
 * @email : scott_x@163.com
 * @description : 服务端内部密钥材料查询结果
 * @status : create
 */
@Data
@NoArgsConstructor
public class ServerSecurityMaterialDTO {

    /**
     * 支付框架颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户 JWT HS256 签名密钥。
     * <p>
     * 该字段只能在服务端内部使用，用于验证 merchant JWT，不允许输出到接口响应或业务日志。
     */
    private String merchantKey;

    /**
     * 商户 JWT 密钥安全指纹，用于日志核对。
     */
    private String merchantKeyFingerprint;

    /**
     * JWT 签名算法。
     */
    private String jwtAlgorithm;

    /**
     * JWT 最大有效期，单位秒。
     */
    private Long jwtExpiresSeconds;

    /**
     * 平台请求体 X.509 DER Base64 公钥。
     */
    private String platformPublicKeyX509Base64;

    /**
     * 平台请求体 PKCS#8 DER Base64 私钥。
     * <p>
     * 该字段只能在服务端内部使用，用于解密商户请求体 data，生产环境应由 KMS/HSM 返回临时可用密钥句柄。
     */
    private String platformPrivateKeyPkcs8Base64;

    /**
     * 平台请求体密钥安全指纹，用于日志核对。
     */
    private String platformKeyFingerprint;

    /**
     * 商户响应 X.509 DER Base64 公钥；默认接入可为空。
     */
    private String merchantResponsePublicKeyX509Base64;

    /**
     * 商户响应公钥安全指纹；默认接入可为空。
     */
    private String merchantResponseKeyFingerprint;
}
