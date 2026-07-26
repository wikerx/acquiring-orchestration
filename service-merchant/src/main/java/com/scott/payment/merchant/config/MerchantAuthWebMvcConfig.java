package com.scott.payment.merchant.config;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;


@Configuration
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAuthWebMvcConfig
 * @date : 2026-06-06 12:00
 * @email : scott_x@163.com
 * @description : MerchantAuthWebMvcConfig Spring 配置类，用于注册当前模块所需 Bean、客户端和拦截器，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class MerchantAuthWebMvcConfig implements WebMvcConfigurer {

    /**
     * 系统鉴权服务，用于拦截器解析和校验商户后台登录态。
     */
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
                "/merchant/auth/default-login-credential",
                "/merchant/auth/verify-code/send",
                "/merchant/auth/mfa/bind-info",
                "/merchant/auth/mfa/bind-confirm",
                "/merchant/auth/mfa/verify",
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
