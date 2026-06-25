package com.scott.payment.component.security.openapi;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OpenAPI 商户对接材料视图，供管理系统和商户系统展示密钥状态、版本、指纹和操作能力。
 */
@Data
public class OpenApiMerchantKeyMaterialVO {

    private String merchantId;
    private String merchantName;
    private String openApiBaseUrl;
    private String sdkVersion;
    private String cryptoMode;

    private String jwtKeyStatus;
    private String jwtAlgorithm;
    private String jwtKeyVersion;
    private String jwtKeyFingerprint;
    private LocalDateTime jwtUpdatedTime;

    private String platformPayloadKeyStatus;
    private String platformPayloadAlgorithm;
    private Integer platformPayloadKeySize;
    private String platformPayloadPublicKeyFingerprint;
    private LocalDateTime platformPayloadUpdatedTime;

    private String merchantResponseKeyStatus;
    private String merchantResponseAlgorithm;
    private Integer merchantResponseKeySize;
    private String merchantResponsePublicKeyFingerprint;
    private LocalDateTime merchantResponseUpdatedTime;

    private boolean merchantResponsePrivateKeyAvailable;
    private boolean canCopyPrivateKey;
    private boolean canDownloadPrivateKey;
    private boolean canRotateJwtKey;
    private boolean canRotatePlatformPayloadKey;
    private boolean canRotateMerchantResponseKey;
}
