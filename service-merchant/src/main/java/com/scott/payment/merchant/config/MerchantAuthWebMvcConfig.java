package com.scott.payment.merchant.config;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAuthWebMvcConfig
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 商户后台接口自动鉴权配置
 * @status : create
 */
@Configuration
public class MerchantAuthWebMvcConfig implements WebMvcConfigurer {

    private final SystemAuthService systemAuthService;

    public MerchantAuthWebMvcConfig(SystemAuthService systemAuthService) {
        this.systemAuthService = systemAuthService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalAuthInterceptor(AuthConstants.APP_MERCHANT, systemAuthService, whitelist()))
                .addPathPatterns("/merchant/**");
    }

    private List<String> whitelist() {
        return List.of(
                "/merchant/auth/login",
                "/merchant/health/**",
                "/actuator/health/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/webjars/**",
                "/favicon.ico",
                "/error"
        );
    }
}
