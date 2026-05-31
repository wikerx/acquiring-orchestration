package com.scott.payment.component.redis.cache;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CacheService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 缓存服务接口
 * @status : create
 */
public interface CacheService {

    /**
     * 写入字符串缓存。
     *
     * @param key        缓存键
     * @param value      缓存值
     * @param ttlSeconds 过期时间，单位秒，小于等于 0 时表示不过期
     */
    void set(String key, String value, long ttlSeconds);

    /**
     * 读取字符串缓存。
     *
     * @param key 缓存键
     * @return 缓存值，不存在时返回 null
     */
    String get(String key);
}
