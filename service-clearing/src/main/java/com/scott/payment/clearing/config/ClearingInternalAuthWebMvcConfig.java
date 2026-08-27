package com.scott.payment.clearing.config;

import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 清分内部接口 HMAC、防重放和调用方白名单配置。 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class ClearingInternalAuthWebMvcConfig implements WebMvcConfigurer {

    private final InternalServiceAuthProperties authProperties;
    private final InternalRequestReplayGuard replayGuard;
    private final ClearingProperties clearingProperties;

    public ClearingInternalAuthWebMvcConfig(InternalServiceAuthProperties authProperties,
                                            InternalRequestReplayGuard replayGuard,
                                            ClearingProperties clearingProperties) {
        this.authProperties = authProperties;
        this.replayGuard = replayGuard;
        this.clearingProperties = clearingProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(authProperties, replayGuard))
                .addPathPatterns("/internal/**");
        registry.addInterceptor(new ClearingInternalCallerInterceptor(clearingProperties))
                .addPathPatterns("/internal/clearing/**");
    }
}
