package com.scott.payment.merchant.config;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.rate.RateLimitFailurePolicy;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 商户找回密码专用限流配置；Redis 异常时拒绝匿名安全操作，避免跨实例降级绕过。
 */
@Configuration
public class MerchantPasswordResetSecurityConfig {

    /**
     * {@code LOCAL_IDENTITY_CAPACITY}常量，统一 {@code MerchantPasswordResetSecurityConfig} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int LOCAL_IDENTITY_CAPACITY = 10_000;

    /**
     * 创建 Redis 故障时 fail-closed 的商户找回密码限流器。
     */
    @Bean
    public RedisSlidingWindowRateLimiter merchantPasswordResetRateLimiter(
            StringRedisTemplate redisTemplate,
            PaymentRedisProperties redisProperties) {
        return new RedisSlidingWindowRateLimiter(redisTemplate, redisProperties,
                LOCAL_IDENTITY_CAPACITY, RateLimitFailurePolicy.FAIL_CLOSED);
    }
}
