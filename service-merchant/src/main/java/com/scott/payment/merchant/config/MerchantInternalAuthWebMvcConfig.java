package com.scott.payment.merchant.config;

import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * service-merchant 内部导出来源 HMAC 鉴权配置，仅保护明确的 /internal/** 服务接口。
 */
@Configuration
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class MerchantInternalAuthWebMvcConfig implements WebMvcConfigurer {
    private final InternalServiceAuthProperties properties;
    private final InternalRequestReplayGuard replayGuard;

    /**
     * 创建 Merchant 内部接口鉴权配置并在启动时校验密钥和路径边界。
     */
    public MerchantInternalAuthWebMvcConfig(InternalServiceAuthProperties properties,
                                            InternalRequestReplayGuard replayGuard) {
        properties.validate();
        this.properties = properties;
        this.replayGuard = replayGuard;
    }

    /**
     * 为全部 Merchant 内部接口注册签名、时间窗和 nonce 防重放校验。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InternalServiceAuthInterceptor(properties, replayGuard))
                .addPathPatterns("/internal/**");
    }
}
