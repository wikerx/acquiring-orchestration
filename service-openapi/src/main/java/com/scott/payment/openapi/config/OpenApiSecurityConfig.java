package com.scott.payment.openapi.config;

import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiSecurityConfig
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : OpenApiSecurityConfig Spring 配置类，用于注册当前模块所需 Bean、客户端和拦截器，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
