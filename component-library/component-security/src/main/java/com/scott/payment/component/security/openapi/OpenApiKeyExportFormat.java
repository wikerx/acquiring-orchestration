package com.scott.payment.component.security.openapi;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyExportFormat
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : OpenApiKeyExportFormat 枚举类型，用于限定业务状态、配置选项或协议取值范围，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
