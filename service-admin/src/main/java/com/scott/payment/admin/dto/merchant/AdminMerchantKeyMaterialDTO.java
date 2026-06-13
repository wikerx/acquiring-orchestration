package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户 OpenAPI 密钥材料详情。
 */
@Data
public class AdminMerchantKeyMaterialDTO {

    private String keyType;

    private String keyName;

    private String owner;

    private String usage;

    private String keyVersion;

    private String algorithm;

    private Integer keySize;

    private Long expiresSeconds;

    private Integer enabled;

    private String fingerprint;

    private String merchantKey;

    private String publicKeyX509Base64;

    private String privateKeyPkcs8Base64;

    private Boolean stored;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    private LocalDateTime gmtModified;
}
