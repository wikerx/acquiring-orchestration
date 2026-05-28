package com.scott.payment.component.redis.lock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisLockService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : Redis 分布式锁服务接口
 * @status : create
 */
public interface RedisLockService {

    boolean tryLock(String key, String value, long ttlSeconds);

    void unlock(String key, String value);
}

