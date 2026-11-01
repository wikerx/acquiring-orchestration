package com.scott.payment.component.security.openapi;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * openAPI商户密钥材料响应模型，位于 公共组件库，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
 */
@Data
public class OpenApiMerchantKeyMaterialVO {

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    private String merchantName;

    private String openApiBaseUrl;

    private String sdkVersion;
    /**
     * 响应中的加解密模式，用于管理端或商户端展示当前处理结果。
     */
    private String cryptoMode;


    private String jwtKeyStatus;
    /**
     * 响应中的{@code jwtAlgorithm}，用于管理端或商户端展示当前处理结果。
     */
    private String jwtAlgorithm;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String jwtKeyVersion;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String jwtKeyFingerprint;
    /**
     * 响应中的{@code jwtUpdatedTime}，用于管理端或商户端展示当前处理结果。
     */
    private LocalDateTime jwtUpdatedTime;


    private String platformPayloadKeyStatus;

    private String platformPayloadAlgorithm;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private Integer platformPayloadKeySize;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String platformPayloadPublicKeyFingerprint;

    private LocalDateTime platformPayloadUpdatedTime;


    private String merchantResponseKeyStatus;
    /**
     * 响应中的商户响应算法，用于管理端或商户端展示当前处理结果。
     */
    private String merchantResponseAlgorithm;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private Integer merchantResponseKeySize;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String merchantResponsePublicKeyFingerprint;
    /**
     * 响应中的商户响应更新时间，用于管理端或商户端展示当前处理结果。
     */
    private LocalDateTime merchantResponseUpdatedTime;

    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private boolean merchantResponsePrivateKeyAvailable;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private boolean canCopyPrivateKey;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private boolean canDownloadPrivateKey;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private boolean canRotateJwtKey;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private boolean canRotatePlatformPayloadKey;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private boolean canRotateMerchantResponseKey;
}
