package com.scott.payment.component.security.openapi;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyAuditService
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : OpenApiKeyAuditService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class OpenApiKeyAuditService {

    /**
     * 判断材料是否包含私钥或完整接入配置。
     *
     * @param keyType 密钥材料类型
     * @return true 表示复制、查看、下载前必须具备私钥材料权限
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
    public boolean isSensitiveMaterial(OpenApiKeyType keyType) {
        return keyType == OpenApiKeyType.JWT_KEY || isPrivateMaterial(keyType);
    }

    /**
     * 判断材料是否属于商户可直接复制或下载的公钥材料。
     *
     * @param keyType 密钥材料类型
     * @return true 表示该材料不包含私钥或共享签名密钥
     */
    public boolean isPublicMaterial(OpenApiKeyType keyType) {
        return keyType == OpenApiKeyType.PLATFORM_PUBLIC_KEY
                || keyType == OpenApiKeyType.MERCHANT_RESPONSE_PUBLIC_KEY;
    }
}
