package com.scott.payment.component.security.openapi;

/**
 * OpenAPI 密钥材料导出格式。
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
