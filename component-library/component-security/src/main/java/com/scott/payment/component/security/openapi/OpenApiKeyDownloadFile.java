package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * OpenAPI 密钥材料下载文件。
 */
@Data
@AllArgsConstructor
public class OpenApiKeyDownloadFile {

    /**
     * 下载文件名。
     */
    private String fileName;

    /**
     * HTTP Content-Type。
     */
    private String contentType;

    /**
     * 文件字节内容。
     */
    private byte[] content;
}
