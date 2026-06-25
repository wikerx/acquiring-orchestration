package com.scott.payment.component.security.openapi;

import lombok.Data;

/**
 * OpenAPI 密钥材料复制或下载请求。
 */
@Data
public class OpenApiKeyExportRequest {

    /**
     * 需要导出的密钥材料类型。
     */
    private OpenApiKeyType keyType;

    /**
     * 导出格式，复制时通常使用 TEXT，下载时使用 TXT、PEM、PROPERTIES 或 ZIP。
     */
    private OpenApiKeyExportFormat exportFormat;
}
