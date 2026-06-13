package com.scott.payment.admin.dto.merchant;

import lombok.Data;

/**
 * 商户 OpenAPI 对接材料响应。
 */
@Data
public class AdminMerchantSecurityMaterialDTO {

    private String merchantId;

    private String merchantName;

    private String merchantKey;

    private String merchantKeyMasked;

    private String jwtAlgorithm;

    private Long jwtExpiresSeconds;

    private String platformPublicKeyX509Base64;

    private String merchantResponsePublicKeyX509Base64;

    private String merchantResponsePrivateKeyPkcs8Base64;

    private Boolean oneTimeSecret;
}
