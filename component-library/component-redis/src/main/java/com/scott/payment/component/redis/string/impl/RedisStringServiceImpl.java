package com.scott.payment.component.redis.string.impl;

import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeySupport;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisStringServiceImpl
 * @date : 2026-05-31 21:48
 * @email : scott_x@163.com
 * @description : Redis String 数据结构服务实现
 * @status : create
 */
@Service
public class RedisStringServiceImpl implements RedisStringService {

    /**
     * RedisTemplate，value 使用统一 JSON 序列化，支持 Java 17 时间类型。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis String 服务实现。
     *
     * @param redisTemplate RedisTemplate
     */
    public RedisStringServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写入字符串或可 JSON 序列化对象。
     *
     * @param key   Redis Key
     * @param value 缓存值
     */
    @Override
    public void set(String key, Object value) {
        RedisKeySupport.requireKey(key);
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 写入字符串或可 JSON 序列化对象，并设置过期时间。
     *
     * @param key   Redis Key
     * @param value 缓存值
     * @param ttl   过期时间
     */
    @Override
    public void set(String key, Object value, Duration ttl) {
        RedisKeySupport.requireKey(key);
        RedisKeySupport.requirePositiveTtl(ttl);
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * 当 Key 不存在时写入缓存。
     *
     * @param key   Redis Key
     * @param value 缓存值
     * @param ttl   过期时间
     * @return 是否写入成功
     */
    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        RedisKeySupport.requireKey(key);
        RedisKeySupport.requirePositiveTtl(ttl);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    /**
     * 获取原始缓存对象。
     *
     * @param key Redis Key
     * @return 缓存对象；不存在时返回 null
     */
    @Override
    public Object get(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return null;
        }
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取指定类型的缓存对象。
     *
     * @param key  Redis Key
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 缓存对象；不存在时返回 null
     */
    @Override
    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("redis value type mismatch, key=" + key);
        }
        return type.cast(value);
    }

    /**
     * 判断 Key 是否存在。
     *
     * @param key Redis Key
     * @return 是否存在
     */
    @Override
    public boolean hasKey(String key) {
        return RedisKeySupport.hasKey(key) && Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 设置 Key 过期时间。
     *
     * @param key Redis Key
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    @Override
    public boolean expire(String key, Duration ttl) {
        RedisKeySupport.requireKey(key);
        RedisKeySupport.requirePositiveTtl(ttl);
        return Boolean.TRUE.equals(redisTemplate.expire(key, ttl));
    }

    /**
     * 获取 Key 剩余过期秒数。
     *
     * @param key Redis Key
     * @return 剩余过期秒数；-1 表示不过期；-2 表示 Key 不存在
     */
    @Override
    public long getExpireSeconds(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return RedisKeySupport.KEY_NOT_EXIST_SECONDS;
        }
        Long expireSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expireSeconds == null ? RedisKeySupport.KEY_NOT_EXIST_SECONDS : expireSeconds;
    }

    /**
     * 删除单个 Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    @Override
    public boolean delete(String key) {
        return RedisKeySupport.hasKey(key) && Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 批量删除 Key。
     *
     * @param keys Redis Key 集合
     * @return 删除数量
     */
    @Override
    public long delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        Long deleted = redisTemplate.delete(keys.stream()
                .filter(RedisKeySupport::hasKey)
                .toList());
        return deleted == null ? 0L : deleted;
    }

    /**
     * 按增量递增数值。
     *
     * @param key   Redis Key
     * @param delta 增量
     * @return 递增后的值
     */
    @Override
    public long increment(String key, long delta) {
        RedisKeySupport.requireKey(key);
        return Objects.requireNonNull(redisTemplate.opsForValue().increment(key, delta));
    }

    /**
     * 按增量递减数值。
     *
     * @param key   Redis Key
     * @param delta 减量
     * @return 递减后的值
     */
    @Override
    public long decrement(String key, long delta) {
        RedisKeySupport.requireKey(key);
        return Objects.requireNonNull(redisTemplate.opsForValue().decrement(key, delta));
    }
}
