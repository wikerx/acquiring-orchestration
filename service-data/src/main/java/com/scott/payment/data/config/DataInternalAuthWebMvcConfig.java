package com.scott.payment.data.config;

import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * service-data 内部接口鉴权配置，为通知补偿等 /internal/** 写操作注册 HMAC-SHA256 服务间签名校验
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class DataInternalAuthWebMvcConfig implements WebMvcConfigurer {

    /**
     * 内部服务签名、时间窗和白名单配置。
     */
    private final InternalServiceAuthProperties properties;

    /**
     * Redis nonce 防重放守卫。
     */
    private final InternalRequestReplayGuard replayGuard;

    /**
     * 创建 service-data 内部接口鉴权配置。
     *
     * @param properties 内部服务签名配置
     * @param replayGuard Redis nonce 防重放守卫
     */
    public DataInternalAuthWebMvcConfig(InternalServiceAuthProperties properties,
                                        InternalRequestReplayGuard replayGuard) {
        properties.validate();
        this.properties = properties;
        this.replayGuard = replayGuard;
    }

    /**
     * 为 /internal/** 注册签名校验，业务接口不允许匿名调用。
     *
     * @param registry MVC 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(properties, replayGuard))
                .addPathPatterns("/internal/**");
    }
}
