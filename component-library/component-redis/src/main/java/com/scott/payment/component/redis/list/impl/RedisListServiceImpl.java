package com.scott.payment.component.redis.list.impl;

import com.scott.payment.component.redis.list.RedisListService;
import com.scott.payment.component.redis.support.RedisKeySupport;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisListServiceImpl
 * @date : 2026-05-31 21:56
 * @email : scott_x@163.com
 * @description : Redis List 数据结构服务实现
 * @status : create
 */
@Service
public class RedisListServiceImpl implements RedisListService {

    /**
     * RedisTemplate，List Value 使用统一 JSON 序列化。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis List 服务实现。
     *
     * @param redisTemplate RedisTemplate
     */
    public RedisListServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 从左侧压入元素。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 压入后的列表长度
     */
    @Override
    public long leftPush(String key, Object value) {
        RedisKeySupport.requireKey(key);
        Long size = redisTemplate.opsForList().leftPush(key, value);
        return size == null ? 0L : size;
    }

    /**
     * 从右侧压入元素。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 压入后的列表长度
     */
    @Override
    public long rightPush(String key, Object value) {
        RedisKeySupport.requireKey(key);
        Long size = redisTemplate.opsForList().rightPush(key, value);
        return size == null ? 0L : size;
    }

    /**
     * 从左侧批量压入元素。
     *
     * @param key    Redis Key
     * @param values 元素集合
     * @return 压入后的列表长度
     */
    @Override
    public long leftPushAll(String key, Collection<?> values) {
        RedisKeySupport.requireKey(key);
        if (values == null || values.isEmpty()) {
            return size(key);
        }
        Long size = redisTemplate.opsForList().leftPushAll(key, values.toArray());
        return size == null ? 0L : size;
    }

    /**
     * 从右侧批量压入元素。
     *
     * @param key    Redis Key
     * @param values 元素集合
     * @return 压入后的列表长度
     */
    @Override
    public long rightPushAll(String key, Collection<?> values) {
        RedisKeySupport.requireKey(key);
        if (values == null || values.isEmpty()) {
            return size(key);
        }
        Long size = redisTemplate.opsForList().rightPushAll(key, values.toArray());
        return size == null ? 0L : size;
    }

    /**
     * 获取指定范围内的列表元素。
     *
     * @param key   Redis Key
     * @param start 起始下标
     * @param end   结束下标，-1 表示末尾
     * @return 列表元素
     */
    @Override
    public List<Object> range(String key, long start, long end) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptyList();
        }
        List<Object> values = redisTemplate.opsForList().range(key, start, end);
        return values == null ? Collections.emptyList() : values;
    }

    /**
     * 按下标获取列表元素。
     *
     * @param key   Redis Key
     * @param index 元素下标
     * @return 元素值；不存在时返回 null
     */
    @Override
    public Object index(String key, long index) {
        if (!RedisKeySupport.hasKey(key)) {
            return null;
        }
        return redisTemplate.opsForList().index(key, index);
    }

    /**
     * 从左侧弹出元素。
     *
     * @param key Redis Key
     * @return 元素值；不存在时返回 null
     */
    @Override
    public Object leftPop(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return null;
        }
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从右侧弹出元素。
     *
     * @param key Redis Key
     * @return 元素值；不存在时返回 null
     */
    @Override
    public Object rightPop(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return null;
        }
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 获取列表长度。
     *
     * @param key Redis Key
     * @return 列表长度
     */
    @Override
    public long size(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return 0L;
        }
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0L : size;
    }

    /**
     * 按下标更新列表元素。
     *
     * @param key   Redis Key
     * @param index 元素下标
     * @param value 新值
     */
    @Override
    public void set(String key, long index, Object value) {
        RedisKeySupport.requireKey(key);
        redisTemplate.opsForList().set(key, index, value);
    }

    /**
     * 删除指定元素。
     *
     * @param key   Redis Key
     * @param count 删除数量，0 表示全部匹配元素
     * @param value 元素值
     * @return 删除数量
     */
    @Override
    public long remove(String key, long count, Object value) {
        if (!RedisKeySupport.hasKey(key)) {
            return 0L;
        }
        Long removed = redisTemplate.opsForList().remove(key, count, value);
        return removed == null ? 0L : removed;
    }

    /**
     * 设置列表 Key 过期时间。
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
