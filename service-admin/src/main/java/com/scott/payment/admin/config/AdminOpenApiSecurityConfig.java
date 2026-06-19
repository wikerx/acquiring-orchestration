package com.scott.payment.admin.config;

import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理后台 OpenAPI 安全材料配置。
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
