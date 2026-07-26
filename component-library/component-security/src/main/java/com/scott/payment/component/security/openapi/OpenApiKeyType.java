package com.scott.payment.component.security.openapi;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyType
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : OpenApiKeyType 枚举类型，用于限定业务状态、配置选项或协议取值范围，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
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
