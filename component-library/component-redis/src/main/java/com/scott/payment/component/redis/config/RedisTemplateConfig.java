package com.scott.payment.component.redis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisTemplateConfig
 * @date : 2026-05-31 20:45
 * @email : scott_x@163.com
 * @description : Redis 序列化配置
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(PaymentRedisProperties.class)
public class RedisTemplateConfig {

    /**
     * 注册统一 RedisTemplate。
     * <p>
     * key/hashKey 使用字符串序列化，value/hashValue 使用 JSON 序列化并支持 Java 17 时间类型，
     * 避免 JDK 原生序列化导致可读性差、跨语言困难和历史类结构变更不兼容。
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @return 统一 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        RedisSerializer<Object> jsonSerializer = PaymentRedisSerializerFactory.create();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
        redisTemplate.setHashKeySerializer(StringRedisSerializer.UTF_8);
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
