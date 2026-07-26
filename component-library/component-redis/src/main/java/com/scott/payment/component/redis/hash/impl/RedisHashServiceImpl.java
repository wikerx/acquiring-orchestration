package com.scott.payment.component.redis.hash.impl;

import com.scott.payment.component.redis.hash.RedisHashService;
import com.scott.payment.component.redis.support.RedisKeySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisHashServiceImpl
 * @date : 2026-05-31 21:52
 * @email : scott_x@163.com
 * @description : Redis Hash 数据结构服务实现
 * @status : create
 */
@Service
@ConditionalOnBean(RedisTemplate.class)
public class RedisHashServiceImpl implements RedisHashService {

    /**
     * RedisTemplate，Hash Value 使用统一 JSON 序列化。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis Hash 服务实现。
     *
     * @param redisTemplate RedisTemplate
     */
    public RedisHashServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 写入单个 Hash 字段。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @param value   Hash 值
     */
    @Override
    public void put(String key, String hashKey, Object value) {
        RedisKeySupport.requireKey(key);
        RedisKeySupport.requireKey(hashKey);
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * 写入单个 Hash 字段并设置整个 Hash Key 的过期时间。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @param value   Hash 值
     * @param ttl     过期时间
     * @return 是否设置过期成功
     */
    @Override
    public boolean put(String key, String hashKey, Object value, Duration ttl) {
        put(key, hashKey, value);
        return expire(key, ttl);
    }

    /**
     * 批量写入 Hash 字段。
     *
     * @param key    Redis Key
     * @param values Hash 字段和值
     */
    @Override
    public void putAll(String key, Map<String, ?> values) {
        RedisKeySupport.requireKey(key);
        if (values == null || values.isEmpty()) {
            return;
        }
        redisTemplate.opsForHash().putAll(key, values);
    }

    /**
     * 批量写入 Hash 字段并设置整个 Hash Key 的过期时间。
     *
     * @param key    Redis Key
     * @param values Hash 字段和值
     * @param ttl    过期时间
     * @return 是否设置过期成功
     */
    @Override
    public boolean putAll(String key, Map<String, ?> values, Duration ttl) {
        putAll(key, values);
        return expire(key, ttl);
    }

    /**
     * 获取单个 Hash 字段值。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @return Hash 字段值；不存在时返回 null
     */
    @Override
    public Object get(String key, String hashKey) {
        if (!RedisKeySupport.hasKey(key) || !RedisKeySupport.hasKey(hashKey)) {
            return null;
        }
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * 获取单个 Hash 字段指定类型值。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @param type    目标类型
     * @param <T>     目标类型泛型
     * @return Hash 字段值；不存在时返回 null
     */
    @Override
    public <T> T get(String key, String hashKey, Class<T> type) {
        Object value = get(key, hashKey);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("redis hash value type mismatch, key=" + key + ", hashKey=" + hashKey);
        }
        return type.cast(value);
    }

    /**
     * 获取所有 Hash 字段和值。
     *
     * @param key Redis Key
     * @return Hash 字段和值
     */
    @Override
    public Map<String, Object> entries(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptyMap();
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<String, Object> result = new LinkedHashMap<>(entries.size());
        entries.forEach((field, value) -> result.put(String.valueOf(field), value));
        return result;
    }

    /**
     * 获取所有 Hash 字段名。
     *
     * @param key Redis Key
     * @return Hash 字段名集合
     */
    @Override
    public Set<String> keys(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptySet();
        }
        return redisTemplate.opsForHash().keys(key).stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }

    /**
     * 获取所有 Hash 字段值。
     *
     * @param key Redis Key
     * @return Hash 字段值列表
     */
    @Override
    public List<Object> values(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptyList();
        }
        return redisTemplate.opsForHash().values(key);
    }

    /**
     * 判断 Hash 字段是否存在。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @return 是否存在
     */
    @Override
    public boolean hasHashKey(String key, String hashKey) {
        return RedisKeySupport.hasKey(key)
                && RedisKeySupport.hasKey(hashKey)
                && Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, hashKey));
    }

    /**
     * 删除 Hash 字段。
     *
     * @param key      Redis Key
     * @param hashKeys Hash 字段
     * @return 删除字段数量
     */
    @Override
    public long deleteFields(String key, String... hashKeys) {
        if (!RedisKeySupport.hasKey(key) || hashKeys == null || hashKeys.length == 0) {
            return 0L;
        }
        return redisTemplate.opsForHash().delete(key, (Object[]) hashKeys);
    }

    /**
     * 删除整个 Hash Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    @Override
    public boolean delete(String key) {
        return RedisKeySupport.hasKey(key) && Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * Hash 字段递增。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @param delta   增量
     * @return 递增后的值
     */
    @Override
    public long increment(String key, String hashKey, long delta) {
        RedisKeySupport.requireKey(key);
        RedisKeySupport.requireKey(hashKey);
        return Objects.requireNonNull(redisTemplate.opsForHash().increment(key, hashKey, delta));
    }

    /**
     * 设置整个 Hash Key 的过期时间。
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
     * 获取整个 Hash Key 的剩余过期秒数。
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
}
