package com.scott.payment.merchant.config;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.rate.RateLimitFailurePolicy;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPasswordResetSecurityConfig
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户找回密码专用限流配置；Redis 异常时拒绝匿名安全操作，避免跨实例降级绕过。
 * @status : create
 */
@Configuration
public class MerchantPasswordResetSecurityConfig {

    private static final int LOCAL_IDENTITY_CAPACITY = 10_000;

    /** 创建 Redis 故障时 fail-closed 的商户找回密码限流器。 */
    @Bean
    public RedisSlidingWindowRateLimiter merchantPasswordResetRateLimiter(
            StringRedisTemplate redisTemplate,
            PaymentRedisProperties redisProperties) {
        return new RedisSlidingWindowRateLimiter(redisTemplate, redisProperties,
                LOCAL_IDENTITY_CAPACITY, RateLimitFailurePolicy.FAIL_CLOSED);
    }
}
