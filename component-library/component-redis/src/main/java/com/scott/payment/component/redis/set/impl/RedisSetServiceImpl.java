package com.scott.payment.component.redis.set.impl;

import com.scott.payment.component.redis.set.RedisSetService;
import com.scott.payment.component.redis.support.RedisKeySupport;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisSetServiceImpl
 * @date : 2026-05-31 22:00
 * @email : scott_x@163.com
 * @description : Redis Set 数据结构服务实现
 * @status : create
 */
@Service
public class RedisSetServiceImpl implements RedisSetService {

    /**
     * RedisTemplate，Set Value 使用统一 JSON 序列化。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis Set 服务实现。
     *
     * @param redisTemplate RedisTemplate
     */
    public RedisSetServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加集合元素。
     *
     * @param key    Redis Key
     * @param values 集合元素
     * @return 新增元素数量
     */
    @Override
    public long add(String key, Object... values) {
        RedisKeySupport.requireKey(key);
        if (values == null || values.length == 0) {
            return 0L;
        }
        Long added = redisTemplate.opsForSet().add(key, values);
        return added == null ? 0L : added;
    }

    /**
     * 添加集合元素并设置过期时间。
     *
     * @param key    Redis Key
     * @param ttl    过期时间
     * @param values 集合元素
     * @return 是否设置过期成功
     */
    @Override
    public boolean add(String key, Duration ttl, Object... values) {
        add(key, values);
        return expire(key, ttl);
    }

    /**
     * 获取集合所有元素。
     *
     * @param key Redis Key
     * @return 集合元素
     */
    @Override
    public Set<Object> members(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptySet();
        }
        Set<Object> members = redisTemplate.opsForSet().members(key);
        return members == null ? Collections.emptySet() : members;
    }

    /**
     * 判断元素是否存在。
     *
     * @param key   Redis Key
     * @param value 集合元素
     * @return 是否存在
     */
    @Override
    public boolean isMember(String key, Object value) {
        return RedisKeySupport.hasKey(key) && Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    /**
     * 获取集合大小。
     *
     * @param key Redis Key
     * @return 集合大小
     */
    @Override
    public long size(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return 0L;
        }
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0L : size;
    }

    /**
     * 移除集合元素。
     *
     * @param key    Redis Key
     * @param values 集合元素
     * @return 移除数量
     */
    @Override
    public long remove(String key, Object... values) {
        if (!RedisKeySupport.hasKey(key) || values == null || values.length == 0) {
            return 0L;
        }
        Long removed = redisTemplate.opsForSet().remove(key, values);
        return removed == null ? 0L : removed;
    }

    /**
     * 删除整个集合 Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    @Override
    public boolean delete(String key) {
        return RedisKeySupport.hasKey(key) && Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 设置集合 Key 过期时间。
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
}
