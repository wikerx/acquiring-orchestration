package com.scott.payment.component.security.openapi;


/**
 * OpenAPI 密钥导出格式枚举，位于 公共组件库，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 */
public enum OpenApiKeyExportFormat {

    /**
     * 纯文本内容，用于复制。
     */
    TEXT,

    /**
     * 文本文件下载。
     */
    TXT,

    /**
     * PEM 密钥文件下载。
     */
    PEM,

    /**
     * properties 配置文件下载。
     */
    PROPERTIES,

    /**
     * ZIP 完整接入包下载。
     */
    ZIP
}
