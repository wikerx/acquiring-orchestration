package com.scott.payment.component.security.openapi;

import lombok.Data;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyExportRequest
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : OpenApiKeyExportRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
