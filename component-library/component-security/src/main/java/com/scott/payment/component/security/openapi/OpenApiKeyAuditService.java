package com.scott.payment.component.security.openapi;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyAuditService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : OpenAPI 密钥操作审计辅助服务，统一判断哪些材料属于敏感导出范围。 <p> 具体审计落库由管理端和商户端各自的操作日志切面完成，本服务保证两个入口使用同一套敏感材料分类。
 * @status : create
 */
public class OpenApiKeyAuditService {

    /**
     * 判断材料是否包含私钥或完整接入配置。
     *
     * @param keyType 密钥材料类型
     * @return true 表示复制、查看、下载前必须具备私钥材料权限
     */
    /**
     * 判断商户 OpenAPI条件是否满足，供业务分支或权限控制使用。
     * @param keyType 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public boolean isPrivateMaterial(OpenApiKeyType keyType) {
        return keyType == OpenApiKeyType.MERCHANT_CONFIG
                || keyType == OpenApiKeyType.MERCHANT_CONFIG_TEXT
                || keyType == OpenApiKeyType.PLATFORM_PRIVATE_KEY
                || keyType == OpenApiKeyType.MERCHANT_RESPONSE_PRIVATE_KEY
                || keyType == OpenApiKeyType.SDK_KIT;
    }

    /**
     * 判断材料是否属于需要二次确认和审计的高敏感材料。
     * <p>
     * merchantKey 是 JWT 共享签名密钥，需要确认和审计，但不按“私钥材料导出”权限拦截。
     *
     * @param keyType 密钥材料类型
     * @return true 表示前端操作前应二次确认
     */
    /**
     * 判断商户 OpenAPI条件是否满足，供业务分支或权限控制使用。
     * @param keyType 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public boolean isSensitiveMaterial(OpenApiKeyType keyType) {
        return keyType == OpenApiKeyType.JWT_KEY || isPrivateMaterial(keyType);
    }

    /**
     * 判断材料是否属于商户可直接复制或下载的公钥材料。
     *
     * @param keyType 密钥材料类型
     * @return true 表示该材料不包含私钥或共享签名密钥
     */
    /**
     * 判断商户 OpenAPI条件是否满足，供业务分支或权限控制使用。
     * @param keyType 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public boolean isPublicMaterial(OpenApiKeyType keyType) {
        return keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY
                || keyType == OpenApiKeyType.MERCHANT_RESPONSE_PUBLIC_KEY;
    }
}
