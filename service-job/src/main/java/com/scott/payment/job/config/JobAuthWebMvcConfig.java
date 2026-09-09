package com.scott.payment.job.config;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobAuthWebMvcConfig
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务认证WebMvc配置类
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class JobAuthWebMvcConfig implements WebMvcConfigurer {

    private final SystemAuthService systemAuthService;
    private final InternalServiceAuthProperties internalServiceAuthProperties;
    private final InternalRequestReplayGuard replayGuard;

    /**
     * 创建 Job 服务鉴权配置。
     *
     * @param systemAuthService 系统内部鉴权服务
     * @param internalServiceAuthProperties Admin → Job HMAC 调用方配置
     * @param replayGuard Redis nonce 防重放守卫
     */
    public JobAuthWebMvcConfig(SystemAuthService systemAuthService,
                               InternalServiceAuthProperties internalServiceAuthProperties,
                               InternalRequestReplayGuard replayGuard) {
        internalServiceAuthProperties.validate();
        this.systemAuthService = systemAuthService;
        this.internalServiceAuthProperties = internalServiceAuthProperties;
        this.replayGuard = replayGuard;
    }

    /**
     * 注册内部接口鉴权拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(internalServiceAuthProperties, replayGuard))
                .addPathPatterns("/internal/**");
        registry.addInterceptor(new InternalAuthInterceptor(AuthConstants.APP_ADMIN, systemAuthService, whitelist()))
                .addPathPatterns("/internal/**");
    }

    /**
     * 定义无需内部鉴权的白名单路径。
     *
     * @return 白名单路径集合
     */
    private List<String> whitelist() {
        return List.of(
                "/actuator/health/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/webjars/**",
                "/favicon.ico",
                "/error"
        );
    }
}
