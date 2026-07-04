package com.scott.payment.component.redis.cache.impl;

import com.scott.payment.component.redis.cache.CacheService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheServiceImpl
 * @date : 2026-05-31 20:46
 * @email : scott_x@163.com
 * @description : Redis 字符串缓存服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Cache Service Impl，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisCacheServiceImpl implements CacheService {

    /**
     * Spring 字符串 Redis 模板，适合存储幂等、锁、短文本缓存等基础数据。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis 字符串缓存服务。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     */
    public RedisCacheServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 写入字符串缓存。
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param ttlSeconds 过期时间，单位秒，小于等于 0 时表示不过期
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ttlSeconds 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void set(String key, String value, long ttlSeconds) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("redis key can not be blank");
        }
        if (ttlSeconds > 0) {
            stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            return;
        }
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 读取字符串缓存。
     *
     * @param key 缓存键
     * @return 缓存值，不存在时返回 null
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public String get(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(key);
    }
}
