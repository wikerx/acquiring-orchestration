package com.scott.payment.component.security.openapi;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyExportRequest
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : openAPI密钥export请求模型，位于 公共组件库，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
 * @status : create
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
