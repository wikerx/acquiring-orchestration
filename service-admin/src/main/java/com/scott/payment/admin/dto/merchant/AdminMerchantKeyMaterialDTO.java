package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantKeyMaterialDTO
 * @date : 2026-06-19 22:06
 * @email : scott_x@163.com
 * @description : 管理后台商户 OpenAPI 密钥材料详情 DTO
 * @status : create
 *
 * <p>用于管理后台查看商户 JWT、平台请求体和响应密钥等材料详情，
 * 含敏感字段，返回与展示时应严格控制权限与脱敏策略。</p>
 */
@Data
public class AdminMerchantKeyMaterialDTO {

    /**
     * 密钥类型，例如 JWT、PLATFORM_PAYLOAD、MERCHANT_RESPONSE。
     */
    private String keyType;

    /**
     * 密钥名称，便于后台识别材料用途。
     */
    private String keyName;

    /**
     * 密钥归属方，例如平台或商户。
     */
    private String owner;

    /**
     * 密钥使用场景说明。
     */
    private String usage;

    /**
     * 密钥版本号，通常用于轮换追踪。
     */
    private String keyVersion;

    /**
     * 加解密或签名算法名称。
     */
    private String algorithm;

    /**
     * 密钥长度，单位 bit，可为空。
     */
    private Integer keySize;

    /**
     * 过期秒数，主要用于 JWT 等带有效期材料。
     */
    private Long expiresSeconds;

    /**
     * 启用标记，通常 1 表示启用，0 表示停用。
     */
    private Integer enabled;

    /**
     * 密钥指纹，用于后台校验与定位材料，不暴露原文。
     */
    private String fingerprint;

    /**
     * 商户对称密钥原文，属于高敏感字段，仅在一次性展示时返回。
     */
    private String merchantKey;

    /**
     * X.509 Base64 编码公钥内容，属于敏感安全材料。
     */
    private String publicKeyX509Base64;

    /**
     * PKCS8 Base64 编码私钥内容，属于高敏感字段，仅受控场景返回。
     */
    private String privateKeyPkcs8Base64;

    /**
     * 是否已持久化到数据库。
     */
    private Boolean stored;

    /**
     * 材料生效时间。
     */
    private LocalDateTime effectiveTime;

    /**
     * 材料失效时间，可为空。
     */
    private LocalDateTime expireTime;

    /**
     * 最近修改时间。
     */
    private LocalDateTime gmtModified;
}
