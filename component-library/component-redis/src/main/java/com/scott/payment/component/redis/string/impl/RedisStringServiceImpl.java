package com.scott.payment.component.redis.string.impl;

import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisStringServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis String Service Impl，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
@ConditionalOnBean(RedisTemplate.class)
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ttl 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ttl 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param type 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ttl 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param keys 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param delta 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param delta 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public long decrement(String key, long delta) {
        RedisKeySupport.requireKey(key);
        return Objects.requireNonNull(redisTemplate.opsForValue().decrement(key, delta));
    }
}
