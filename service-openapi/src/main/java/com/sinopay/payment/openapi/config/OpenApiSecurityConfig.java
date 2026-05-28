package com.sinopay.payment.openapi.config;

import com.sinopay.payment.component.security.jwt.MerchantJwtVerifier;
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
}
