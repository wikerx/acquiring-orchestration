package com.scott.payment.payment.config;

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
 * @classname : PaymentInternalAuthWebMvcConfig
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 收单支付内部服务鉴权配置，为 service-payment 的 /internal/** 接口注册 HMAC 签名校验。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class PaymentInternalAuthWebMvcConfig implements WebMvcConfigurer {

    /**
     * 内部服务签名配置。
     */
    private final InternalServiceAuthProperties internalServiceAuthProperties;

    /** Redis nonce 防重放守卫。 */
    private final InternalRequestReplayGuard replayGuard;

    /**
     * 创建收单支付内部服务鉴权配置。
     *
     * @param internalServiceAuthProperties 内部服务签名配置
     * @param replayGuard Redis nonce 防重放守卫
     */
    public PaymentInternalAuthWebMvcConfig(InternalServiceAuthProperties internalServiceAuthProperties,
                                           InternalRequestReplayGuard replayGuard) {
        internalServiceAuthProperties.validate();
        this.internalServiceAuthProperties = internalServiceAuthProperties;
        this.replayGuard = replayGuard;
    }

    /**
     * 注册 /internal/** 内部服务签名拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(internalServiceAuthProperties, replayGuard))
                .addPathPatterns("/internal/**");
    }
}
