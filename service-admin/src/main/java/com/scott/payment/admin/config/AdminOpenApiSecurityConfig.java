package com.scott.payment.admin.config;

import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOpenApiSecurityConfig
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台OpenApiSecurity配置类
 * @status : create
 */

@Configuration
public class AdminOpenApiSecurityConfig {

    /**
     * 注册 OpenAPI 密钥材料工厂，供后台商户管理场景生成与轮换密钥。
     *
     * @return 密钥材料工厂
     */
    @Bean
    public OpenApiKeyMaterialFactory openApiKeyMaterialFactory() {
        return new OpenApiKeyMaterialFactory();
    }
}
