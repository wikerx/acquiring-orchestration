package com.scott.payment.clearing.config;

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
 * @classname : ClearingInternalAuthWebMvcConfig
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分内部接口 HMAC、防重放和调用方白名单配置。
 * @status : update
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class ClearingInternalAuthWebMvcConfig implements WebMvcConfigurer {

    private final InternalServiceAuthProperties authProperties;
    private final InternalRequestReplayGuard replayGuard;
    private final ClearingProperties clearingProperties;

    public ClearingInternalAuthWebMvcConfig(InternalServiceAuthProperties authProperties,
                                            InternalRequestReplayGuard replayGuard,
                                            ClearingProperties clearingProperties) {
        authProperties.validate();
        this.authProperties = authProperties;
        this.replayGuard = replayGuard;
        this.clearingProperties = clearingProperties;
    }

    /**
     * 对全部内部接口先执行 HMAC 与防重放校验，再对清分路径叠加 Admin/Job 调用方授权。
     *
     * @param registry Spring MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(authProperties, replayGuard))
                .addPathPatterns("/internal/**");
        registry.addInterceptor(new ClearingInternalCallerInterceptor(clearingProperties))
                .addPathPatterns("/internal/clearing/**");
    }
}
