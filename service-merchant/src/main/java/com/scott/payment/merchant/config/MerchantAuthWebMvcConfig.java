package com.scott.payment.merchant.config;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;


/**
 * 商户认证 Web 配置，注册认证拦截器及无需登录的公开路径。
 */
@Configuration
public class MerchantAuthWebMvcConfig implements WebMvcConfigurer {

    /**
     * 系统鉴权服务，用于拦截器解析和校验商户后台登录态。
     */
    private final SystemAuthService systemAuthService;
    private final MerchantDefaultLoginCredentialProperties defaultLoginProperties;
    private final Environment environment;

    /**
     * 创建商户后台鉴权拦截配置。
     *
     * @param systemAuthService 系统鉴权服务
     * @param defaultLoginProperties 本地开发默认登录凭据配置
     * @param environment 当前 Spring 环境
     */
    public MerchantAuthWebMvcConfig(SystemAuthService systemAuthService,
                                    MerchantDefaultLoginCredentialProperties defaultLoginProperties,
                                    Environment environment) {
        this.systemAuthService = systemAuthService;
        this.defaultLoginProperties = defaultLoginProperties;
        this.environment = environment;
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
        List<String> paths = new ArrayList<>(List.of(
                "/merchant/auth/login",
                "/merchant/auth/verify-code/send",
                "/merchant/auth/mfa/bind-info",
                "/merchant/auth/mfa/bind-confirm",
                "/merchant/auth/mfa/verify",
                "/merchant/auth/password-reset/request",
                "/merchant/auth/password-reset/email/verify",
                "/merchant/auth/password-reset/mfa/verify",
                "/merchant/auth/password-reset/confirm",
                "/merchant/auth/email-verification/verify",
                "/merchant/auth/account-activation/complete",
                "/merchant/health/**",
                "/actuator/health/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/webjars/**",
                "/favicon.ico",
                "/error"
        ));
        if (defaultLoginProperties.isEnabled()
                && environment.acceptsProfiles(Profiles.of("dev", "test", "local"))) {
            paths.add("/merchant/auth/default-login-credential");
        }
        return List.copyOf(paths);
    }
}
