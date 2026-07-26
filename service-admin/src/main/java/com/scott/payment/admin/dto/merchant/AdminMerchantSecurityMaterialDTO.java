package com.scott.payment.admin.dto.merchant;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantSecurityMaterialDTO
 * @date : 2026-06-19 22:08
 * @email : scott_x@163.com
 * @description : 管理后台商户 OpenAPI 对接材料响应 DTO
 * @status : create
 *
 * <p>用于一次性返回商户 OpenAPI 对接所需的敏感安全材料，
 * 包含对称密钥、公钥和私钥等高敏感信息，调用方必须受控存储。</p>
 */
@Data
public class AdminMerchantSecurityMaterialDTO {

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户名称。
     */
    private String merchantName;

    /**
     * 商户 JWT 对称密钥原文，属于高敏感字段。
     */
    private String merchantKey;

    /**
     * 商户 JWT 对称密钥脱敏值，用于页面提示与二次确认。
     */
    private String merchantKeyMasked;

    /**
     * JWT 算法名称。
     */
    private String jwtAlgorithm;

    /**
     * JWT 有效期，单位秒。
     */
    private Long jwtExpiresSeconds;

    /**
     * 平台请求体公钥，X.509 Base64 编码。
     */
    private String platformPublicKeyX509Base64;

    /**
     * 商户响应公钥，X.509 Base64 编码。
     */
    private String merchantResponsePublicKeyX509Base64;

    /**
     * 商户响应私钥，PKCS8 Base64 编码，属于高敏感字段。
     */
    private String merchantResponsePrivateKeyPkcs8Base64;

    /**
     * 是否为一次性敏感材料响应，true 表示调用方应立即妥善保存。
     */
    private Boolean oneTimeSecret;
}
