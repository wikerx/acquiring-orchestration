package com.scott.payment.component.redis.security;

import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalRequestReplayGuardAutoConfiguration
 * @date : 2026-08-20 23:50
 * @email : scott_x@163.com
 * @description : 在 Redis 基础设施可用时注册内部服务 HMAC nonce 防重放实现
 * @status : create
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@EnableConfigurationProperties(PaymentRedisProperties.class)
public class InternalRequestReplayGuardAutoConfiguration {

    /**
     * 注册跨实例共享的内部请求防重放守卫。
     *
     * @param redisTemplate Redis 字符串操作入口
     * @param keyResolver 统一 Redis 物理 Key 构造器
     * @return Redis nonce 防重放实现
     */
    @Bean
    @ConditionalOnMissingBean(InternalRequestReplayGuard.class)
    public InternalRequestReplayGuard internalRequestReplayGuard(
            StringRedisTemplate redisTemplate,
            PaymentRedisKeyResolver keyResolver) {
        return new RedisInternalRequestReplayGuard(redisTemplate, keyResolver);
    }
}
