package com.scott.payment.openapi.config;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRateLimitConfig
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : OpenAPI 商户滑动窗口限流器装配，与 MVC 拦截器配置解耦以避免认证链 Bean 循环依赖
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(OpenApiRateLimitProperties.class)
public class OpenApiRateLimitConfig {

    /**
     * 使用 OpenAPI 配置的本地身份容量和 Redis 故障策略创建商户维度限流器。
     *
     * @param redisTemplate Redis 字符串客户端
     * @param redisProperties Redis 环境 Key 前缀配置
     * @param rateLimitProperties OpenAPI 商户限流配置
     * @return 商户认证链共用的分布式滑动窗口限流器
     */
    @Bean
    public RedisSlidingWindowRateLimiter redisSlidingWindowRateLimiter(
            StringRedisTemplate redisTemplate,
            PaymentRedisProperties redisProperties,
            OpenApiRateLimitProperties rateLimitProperties) {
        return new RedisSlidingWindowRateLimiter(
                redisTemplate,
                redisProperties,
                rateLimitProperties.getLocalMaxIdentities(),
                rateLimitProperties.getFailurePolicy());
    }
}
