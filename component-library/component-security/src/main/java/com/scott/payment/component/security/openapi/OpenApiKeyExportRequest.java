package com.scott.payment.component.security.openapi;

import lombok.Data;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyExportRequest
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : Open API Key Export Request 传输模型，位于 公共组件库，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
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
