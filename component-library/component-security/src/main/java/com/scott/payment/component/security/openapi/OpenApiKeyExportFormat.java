package com.scott.payment.component.security.openapi;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyExportFormat
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : OpenAPI 密钥材料导出格式。
 * @status : create
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
