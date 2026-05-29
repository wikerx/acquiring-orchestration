package com.scott.payment.openapi.config;

import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiSecurityConfig
 * @date : 2026-05-28 11:42
 * @email : scott_x@163.com
 * @description : 开放接口安全配置
 * @status : create
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
}
