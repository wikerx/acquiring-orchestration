package com.scott.payment.openapi.dto.security;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantSecurityMaterialDTO
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户开户后生成的对接密钥材料
 * @status : create
 */
@Data
@NoArgsConstructor
public class OpenApiMerchantSecurityMaterialDTO {

    /**
     * 支付框架颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户主体名称，用于测试日志和开户结果展示。
     */
    private String merchantName;

    /**
     * 商户 JWT HS256 签名密钥。
     * <p>
     * 该密钥由平台生成并安全交付给商户，商户用于生成 authorization JWT，平台用于验签。
     */
    private String merchantKey;

    /**
     * 商户 JWT 签名算法，当前固定为 HS256。
     */
    private String jwtAlgorithm;

    /**
     * JWT 最大有效期，单位秒，当前固定为 180 秒。
     */
    private Long jwtExpiresSeconds;

    /**
     * 平台请求体加密公钥编号，商户加密请求体 data 时写入 kid。
     */
    private String platformPayloadKeyId;

    /**
     * 平台请求体 X.509 DER Base64 公钥，商户侧可保存并用于加密请求体。
     */
    private String platformPublicKeyX509Base64;

    /**
     * 平台请求体 PEM 公钥，方便 Java、PHP、Go、C/OpenSSL 等不同技术栈读取。
     */
    private String platformPublicKeyPem;

    /**
     * 商户响应公钥编号，平台响应加密增强模式写入 kid；默认对接可为空。
     */
    private String merchantResponseKeyId;

    /**
     * 商户响应 X.509 DER Base64 公钥，平台保存并用于加密响应 data；默认对接可为空。
     */
    private String merchantResponsePublicKeyX509Base64;

    /**
     * 商户响应 PKCS#8 DER Base64 私钥。
     * <p>
     * 该字段只用于测试展示商户侧解密流程；默认对接可为空，生产中平台不应保存商户响应私钥。
     */
    private String merchantResponsePrivateKeyPkcs8Base64;

    /**
     * 商户响应 PKCS#8 PEM 私钥，商户侧用于解密平台响应 data；默认对接可为空。
     */
    private String merchantResponsePrivateKeyPem;
}
