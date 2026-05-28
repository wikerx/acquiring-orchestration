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

    void set(String key, String value, long ttlSeconds);

    String get(String key);
}

