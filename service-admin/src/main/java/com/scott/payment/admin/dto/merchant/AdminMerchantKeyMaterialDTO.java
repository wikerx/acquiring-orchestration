package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantKeyMaterialDTO
 * @date : 2026-06-13 17:17
 * @email : scott_x@163.com
 * @description : Admin 商户密钥材料 DTO，仅在受控创建、轮换或下载流程中短暂承载公钥、密文和指纹信息。
 * @status : create
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
     * 预留字段，列表接口不返回商户对称密钥原文。
     */
    private String merchantKey;

    /**
     * 预留字段，列表接口不返回 X.509 Base64 编码公钥原文。
     */
    private String publicKeyX509Base64;

    /**
     * 预留字段，列表接口不返回 PKCS8 Base64 编码私钥原文。
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
