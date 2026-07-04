package com.scott.payment.component.security.openapi;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantKeyMaterialVO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Merchant Key Material 视图对象，位于 component-library/component-security 的安全组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class OpenApiMerchantKeyMaterialVO {

    /**
     * 商户 OpenAPI标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private String merchantId;
    /**
     * 商户 OpenAPI业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String merchantName;
    /**
     * 商户 OpenAPI业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String openApiBaseUrl;
    /**
     * 商户 OpenAPI业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String sdkVersion;
    /**
     * 商户 OpenAPI业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String cryptoMode;

    /**
     * 商户 OpenAPI状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String jwtKeyStatus;
    /**
     * 商户 OpenAPI业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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
     * 商户 OpenAPI时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime jwtUpdatedTime;

    /**
     * 商户 OpenAPI状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String platformPayloadKeyStatus;
    /**
     * 商户 OpenAPI业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String platformPayloadAlgorithm;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private Integer platformPayloadKeySize;
    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String platformPayloadPublicKeyFingerprint;
    /**
     * 商户 OpenAPI时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime platformPayloadUpdatedTime;

    /**
     * 商户 OpenAPI状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String merchantResponseKeyStatus;
    /**
     * 商户 OpenAPI业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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
     * 商户 OpenAPI时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
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
