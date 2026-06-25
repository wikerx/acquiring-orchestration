package com.scott.payment.component.security.openapi;

/**
 * OpenAPI 密钥材料导出类型，限定管理端和商户端可以复制或下载的材料范围。
 */
public enum OpenApiKeyType {

    /**
     * 商户 JWT HS256 签名密钥。
     */
    JWT_KEY,

    /**
     * 平台请求加密公钥，商户使用它加密请求 data。
     */
    PLATFORM_PUBLIC_KEY,

    /**
     * 平台请求解密私钥，平台使用它解密商户请求 data，仅管理端可受控查看和导出。
     */
    PLATFORM_PRIVATE_KEY,

    /**
     * 平台请求加密密钥轮换别名，兼容管理端和商户端轮换请求。
     */
    PLATFORM_PAYLOAD_KEY,

    /**
     * 商户响应公钥，平台使用它加密成功响应 data。
     */
    MERCHANT_RESPONSE_PUBLIC_KEY,

    /**
     * 商户响应私钥，商户使用它解密平台成功响应 data。
     */
    MERCHANT_RESPONSE_PRIVATE_KEY,

    /**
     * 商户响应密钥轮换别名，兼容管理端和商户端轮换请求。
     */
    MERCHANT_RESPONSE_KEY,

    /**
     * 推荐的文件路径版 merchant-config.properties。
     */
    MERCHANT_CONFIG,

    /**
     * 兼容旧 SDK 的文本版 merchant-config-text.properties。
     */
    MERCHANT_CONFIG_TEXT,

    /**
     * 包含配置文件和 PEM 文件的完整 SDK 接入材料包。
     */
    SDK_KIT
}
