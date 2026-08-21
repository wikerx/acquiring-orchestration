package com.scott.payment.admin.config;

import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminInternalAuthWebMvcConfig
 * @date : 2026-08-06 00:00
 * @description : service-admin 内部接口 HMAC 鉴权配置，保护商户访问配置查询与提交边界。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class AdminInternalAuthWebMvcConfig implements WebMvcConfigurer {

    private final InternalServiceAuthProperties properties;

    /** Redis nonce 防重放守卫。 */
    private final InternalRequestReplayGuard replayGuard;

    /**
     * 创建内部接口鉴权配置。
     *
     * @param properties 内部服务签名配置
     * @param replayGuard Redis nonce 防重放守卫
     */
    public AdminInternalAuthWebMvcConfig(InternalServiceAuthProperties properties,
                                         InternalRequestReplayGuard replayGuard) {
        this.properties = properties;
        this.replayGuard = replayGuard;
    }

    /** 注册全部 /internal/** 路径的签名拦截器。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(properties, replayGuard))
                .addPathPatterns("/internal/**");
    }
}
