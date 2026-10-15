package com.scott.payment.admin.config;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.rate.RateLimitFailurePolicy;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPasswordResetSecurityConfig
 * @date : 2026-10-14 00:00
 * @email : scott_x@163.com
 * @description : 管理后台找回密码安全配置，为匿名恢复入口提供跨实例原子限流并在 Redis 故障时拒绝放行。
 * @status : create
 */
@Configuration
public class AdminPasswordResetSecurityConfig {

    private static final int LOCAL_IDENTITY_CAPACITY = 10_000;

    /**
     * 创建找回密码专用滑动窗口限流器。
     *
     * @param redisTemplate Redis 字符串访问模板
     * @param redisProperties Redis 物理键命名配置
     * @return Redis 故障时 fail-closed 的原子限流器
     */
    @Bean
    public RedisSlidingWindowRateLimiter adminPasswordResetRateLimiter(
            StringRedisTemplate redisTemplate,
            PaymentRedisProperties redisProperties) {
        return new RedisSlidingWindowRateLimiter(
                redisTemplate,
                redisProperties,
                LOCAL_IDENTITY_CAPACITY,
                RateLimitFailurePolicy.FAIL_CLOSED
        );
    }
}
