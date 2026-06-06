package com.scott.payment.admin.config;

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
 * @classname : AdminAuthWebMvcConfig
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台接口自动鉴权配置
 * @status : create
 */
@Configuration
public class AdminAuthWebMvcConfig implements WebMvcConfigurer {

    private final SystemAuthService systemAuthService;

    public AdminAuthWebMvcConfig(SystemAuthService systemAuthService) {
        this.systemAuthService = systemAuthService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalAuthInterceptor(AuthConstants.APP_ADMIN, systemAuthService, whitelist()))
                .addPathPatterns("/admin/**");
    }

    private List<String> whitelist() {
        return List.of(
                "/admin/auth/login",
                "/admin/health/**",
                "/actuator/health/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/webjars/**",
                "/favicon.ico",
                "/error"
        );
    }
}
