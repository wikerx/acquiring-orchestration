package com.scott.payment.component.security.openapi;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyExportFormat
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : Open API Key Export Format 枚举，位于 公共组件库，定义交易状态、配置类型或协议结果的受控取值，供状态机、接口返回和日志字段统一引用。
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
