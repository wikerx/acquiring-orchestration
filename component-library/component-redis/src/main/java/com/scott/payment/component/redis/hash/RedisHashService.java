package com.scott.payment.component.redis.hash;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisHashService
 * @date : 2026-05-31 21:52
 * @email : scott_x@163.com
 * @description : Redis Hash 数据结构服务
 * @status : create
 */
public interface RedisHashService {

    /**
     * 写入单个 Hash 字段。
     *
     * @param key      Redis Key
     * @param hashKey  Hash 字段
     * @param value    Hash 值
     */
    void put(String key, String hashKey, Object value);

    /**
     * 写入单个 Hash 字段并设置整个 Hash Key 的过期时间。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @param value   Hash 值
     * @param ttl     过期时间
     * @return 是否设置过期成功
     */
    boolean put(String key, String hashKey, Object value, Duration ttl);

    /**
     * 批量写入 Hash 字段。
     *
     * @param key    Redis Key
     * @param values Hash 字段和值
     */
    void putAll(String key, Map<String, ?> values);

    /**
     * 批量写入 Hash 字段并设置整个 Hash Key 的过期时间。
     *
     * @param key    Redis Key
     * @param values Hash 字段和值
     * @param ttl    过期时间
     * @return 是否设置过期成功
     */
    boolean putAll(String key, Map<String, ?> values, Duration ttl);

    /**
     * 获取单个 Hash 字段值。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @return Hash 字段值；不存在时返回 null
     */
    Object get(String key, String hashKey);

    /**
     * 获取单个 Hash 字段指定类型值。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @param type    目标类型
     * @param <T>     目标类型泛型
     * @return Hash 字段值；不存在时返回 null
     */
    <T> T get(String key, String hashKey, Class<T> type);

    /**
     * 获取所有 Hash 字段和值。
     *
     * @param key Redis Key
     * @return Hash 字段和值
     */
    Map<String, Object> entries(String key);

    /**
     * 获取所有 Hash 字段名。
     *
     * @param key Redis Key
     * @return Hash 字段名集合
     */
    Set<String> keys(String key);

    /**
     * 获取所有 Hash 字段值。
     *
     * @param key Redis Key
     * @return Hash 字段值列表
     */
    List<Object> values(String key);

    /**
     * 判断 Hash 字段是否存在。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @return 是否存在
     */
    boolean hasHashKey(String key, String hashKey);

    /**
     * 删除 Hash 字段。
     *
     * @param key      Redis Key
     * @param hashKeys Hash 字段
     * @return 删除字段数量
     */
    long deleteFields(String key, String... hashKeys);

    /**
     * 删除整个 Hash Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    boolean delete(String key);

    /**
     * Hash 字段递增。
     *
     * @param key     Redis Key
     * @param hashKey Hash 字段
     * @param delta   增量
     * @return 递增后的值
     */
    long increment(String key, String hashKey, long delta);

    /**
     * 设置整个 Hash Key 的过期时间。
     *
     * @param key Redis Key
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    boolean expire(String key, Duration ttl);

    /**
     * 获取整个 Hash Key 的剩余过期秒数。
     *
     * @param key Redis Key
     * @return 剩余过期秒数；-1 表示不过期；-2 表示 Key 不存在
     */
    long getExpireSeconds(String key);
}
