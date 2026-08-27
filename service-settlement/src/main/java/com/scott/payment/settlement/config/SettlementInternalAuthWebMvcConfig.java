package com.scott.payment.settlement.config;

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
 * @classname : SettlementInternalAuthWebMvcConfig
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 结算内部接口 HMAC、Redis nonce 防重放和调用服务身份双层拦截配置。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class SettlementInternalAuthWebMvcConfig implements WebMvcConfigurer {

    private final InternalServiceAuthProperties authProperties;
    private final InternalRequestReplayGuard replayGuard;

    public SettlementInternalAuthWebMvcConfig(InternalServiceAuthProperties authProperties,
                                              InternalRequestReplayGuard replayGuard) {
        this.authProperties = authProperties;
        this.replayGuard = replayGuard;
    }

    /** 先验证签名和 nonce，再验证调用方必须是 service-admin。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(authProperties, replayGuard))
                .addPathPatterns("/internal/**");
        registry.addInterceptor(new SettlementInternalCallerInterceptor())
                .addPathPatterns("/internal/settlement/**");
    }
}
