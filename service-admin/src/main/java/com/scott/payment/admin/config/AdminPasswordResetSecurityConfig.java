package com.scott.payment.admin.config;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.rate.RateLimitFailurePolicy;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 管理后台找回密码安全配置，为匿名恢复入口提供跨实例原子限流并在 Redis 故障时拒绝放行。
 */
@Configuration
public class AdminPasswordResetSecurityConfig {

    /**
     * {@code LOCAL_IDENTITY_CAPACITY}常量，统一 {@code AdminPasswordResetSecurityConfig} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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
