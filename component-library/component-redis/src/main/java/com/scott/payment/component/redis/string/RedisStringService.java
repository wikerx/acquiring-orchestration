package com.scott.payment.component.redis.string;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisStringService
 * @date : 2026-05-31 21:48
 * @email : scott_x@163.com
 * @description : Redis String 数据结构服务
 * @status : create
 */
public interface RedisStringService {

    /**
     * 写入字符串或可 JSON 序列化对象。
     *
     * @param key   Redis Key
     * @param value 缓存值
     */
    void set(String key, Object value);

    /**
     * 写入字符串或可 JSON 序列化对象，并设置过期时间。
     *
     * @param key   Redis Key
     * @param value 缓存值
     * @param ttl   过期时间
     */
    void set(String key, Object value, Duration ttl);

    /**
     * 当 Key 不存在时写入缓存。
     *
     * @param key   Redis Key
     * @param value 缓存值
     * @param ttl   过期时间
     * @return 是否写入成功
     */
    boolean setIfAbsent(String key, Object value, Duration ttl);

    /**
     * 获取原始缓存对象。
     *
     * @param key Redis Key
     * @return 缓存对象；不存在时返回 null
     */
    Object get(String key);

    /**
     * 获取指定类型的缓存对象。
     *
     * @param key  Redis Key
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 缓存对象；不存在时返回 null
     */
    <T> T get(String key, Class<T> type);

    /**
     * 判断 Key 是否存在。
     *
     * @param key Redis Key
     * @return 是否存在
     */
    boolean hasKey(String key);

    /**
     * 设置 Key 过期时间。
     *
     * @param key Redis Key
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    boolean expire(String key, Duration ttl);

    /**
     * 获取 Key 剩余过期秒数。
     *
     * @param key Redis Key
     * @return 剩余过期秒数；-1 表示不过期；-2 表示 Key 不存在
     */
    long getExpireSeconds(String key);

    /**
     * 删除单个 Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    boolean delete(String key);

    /**
     * 按增量递增数值。
     *
     * @param key   Redis Key
     * @param delta 增量
     * @return 递增后的值
     */
    long increment(String key, long delta);

    /**
     * 按增量递减数值。
     *
     * @param key   Redis Key
     * @param delta 减量
     * @return 递减后的值
     */
    long decrement(String key, long delta);
}
