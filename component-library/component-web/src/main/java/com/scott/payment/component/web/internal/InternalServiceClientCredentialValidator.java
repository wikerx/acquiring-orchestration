package com.scott.payment.component.web.internal;

import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceClientCredentialValidator
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : 内部 HTTP 客户端身份与 HMAC 密钥启动门禁；调用方身份由服务代码固定，配置中心只提供
 * 对应调用边的 active 密钥，并拒绝空值、未解析占位符、历史开发弱密钥和不足 32 字符的密钥。
 * @status : create
 */
public final class InternalServiceClientCredentialValidator {

    private static final String DEVELOPMENT_SECRET = "dev-internal-service-secret";

    private InternalServiceClientCredentialValidator() {
    }

    /**
     * 校验内部客户端固定身份和当前签名密钥。
     *
     * @param propertyPrefix 错误消息使用的非敏感配置前缀
     * @param expectedCaller 代码声明的固定调用方
     * @param actualCaller 配置中心绑定的调用方
     * @param secret 当前 active HMAC 密钥
     */
    public static void validate(String propertyPrefix,
                                String expectedCaller,
                                String actualCaller,
                                String secret) {
        if (!expectedCaller.equals(actualCaller)) {
            throw new IllegalStateException(propertyPrefix + " caller must be " + expectedCaller);
        }
        if (!StringUtils.hasText(secret) || secret.contains("${")) {
            throw new IllegalStateException(propertyPrefix + " internal secret is required");
        }
        String normalized = secret.trim();
        if (DEVELOPMENT_SECRET.equals(normalized)
                || normalized.length() < InternalServiceAuthProperties.MIN_SECRET_LENGTH) {
            throw new IllegalStateException(propertyPrefix + " internal secret is too weak");
        }
    }
}
