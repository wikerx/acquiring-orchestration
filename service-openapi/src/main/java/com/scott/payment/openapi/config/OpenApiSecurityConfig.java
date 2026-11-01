package com.scott.payment.openapi.config;

import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 注册商户 JWT 验签、报文加解密和密钥材料生成所需的无状态安全组件。
 */
@Configuration
public class OpenApiSecurityConfig {

    /**
     * 注册商户 JWT 验签器。
     *
     * @return 商户 JWT 验签器
     */
    @Bean
    public MerchantJwtVerifier merchantJwtVerifier() {
        return new MerchantJwtVerifier();
    }

    /**
     * 注册 OpenAPI 报文加解密工具。
     *
     * @return OpenAPI 报文混合加密工具
     */
    @Bean
    public OpenApiPayloadCrypto openApiPayloadCrypto() {
        return new OpenApiPayloadCrypto();
    }

    /**
     * 注册 OpenAPI 密钥材料生成入口。
     *
     * @return OpenAPI 商户密钥材料生成器
     */
    @Bean
    public OpenApiKeyMaterialFactory openApiKeyMaterialFactory() {
        return new OpenApiKeyMaterialFactory();
    }
}
