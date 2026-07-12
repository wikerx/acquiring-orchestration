package com.scott.payment.component.security.openapi;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyExportRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Key Export 请求对象，位于 component-library/component-security 的安全组件层，用于承载该模块对应的业务职责和数据流转边界。
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
