package com.scott.payment.openapi.config;

import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiSecurityConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Security 配置，位于 service-openapi 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
public class OpenApiSecurityConfig {

    /**
     * 注册商户 JWT 验签器。
     *
     * @return 商户 JWT 验签器
     */
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    public OpenApiKeyMaterialFactory openApiKeyMaterialFactory() {
        return new OpenApiKeyMaterialFactory();
    }
}
