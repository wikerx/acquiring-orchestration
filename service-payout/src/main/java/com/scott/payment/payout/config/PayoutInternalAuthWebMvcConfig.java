package com.scott.payment.payout.config;

import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 为代付内部接口注册 HMAC 签名与 nonce 防重放校验。
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class PayoutInternalAuthWebMvcConfig implements WebMvcConfigurer {

    /**
     * 内部服务签名配置。
     */
    private final InternalServiceAuthProperties internalServiceAuthProperties;

    /**
     * Redis nonce 防重放守卫。
     */
    private final InternalRequestReplayGuard replayGuard;

    /**
     * 创建代付内部服务鉴权配置。
     *
     * @param internalServiceAuthProperties 内部服务签名配置
     * @param replayGuard Redis nonce 防重放守卫
     */
    public PayoutInternalAuthWebMvcConfig(InternalServiceAuthProperties internalServiceAuthProperties,
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
