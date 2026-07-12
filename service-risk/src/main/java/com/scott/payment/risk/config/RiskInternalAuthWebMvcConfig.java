package com.scott.payment.risk.config;

import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskInternalAuthWebMvcConfig
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控内部服务鉴权配置，为 service-risk 的 /internal/** 接口注册 HMAC 签名校验。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class RiskInternalAuthWebMvcConfig implements WebMvcConfigurer {

    /**
     * 内部服务签名配置。
     */
    private final InternalServiceAuthProperties internalServiceAuthProperties;

    /**
     * 创建风控内部服务鉴权配置。
     *
     * @param internalServiceAuthProperties 内部服务签名配置
     */
    public RiskInternalAuthWebMvcConfig(InternalServiceAuthProperties internalServiceAuthProperties) {
        this.internalServiceAuthProperties = internalServiceAuthProperties;
    }

    /**
     * 注册 /internal/** 内部服务签名拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(internalServiceAuthProperties))
                .addPathPatterns("/internal/**");
    }
}
