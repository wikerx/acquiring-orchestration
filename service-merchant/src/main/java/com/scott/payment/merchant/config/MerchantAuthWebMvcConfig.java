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

    /**
     * 创建商户后台鉴权拦截配置。
     *
     * @param systemAuthService 系统鉴权服务
     */
    public MerchantAuthWebMvcConfig(SystemAuthService systemAuthService) {
        this.systemAuthService = systemAuthService;
    }

    /**
     * 为商户后台接口注册统一鉴权拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalAuthInterceptor(AuthConstants.APP_MERCHANT, systemAuthService, whitelist()))
                .addPathPatterns("/merchant/**");
    }

    /**
     * 定义无需登录即可访问的商户后台白名单路径。
     *
     * @return 白名单路径集合
     */
    private List<String> whitelist() {
        return List.of(
                "/merchant/auth/login",
                "/merchant/auth/verify-code/send",
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
