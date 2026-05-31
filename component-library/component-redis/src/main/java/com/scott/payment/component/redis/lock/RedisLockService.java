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

    /**
     * 尝试获取 Redis 分布式锁。
     *
     * @param key        锁键
     * @param value      锁值，建议使用请求唯一号或线程唯一标识
     * @param ttlSeconds 锁过期时间，单位秒
     * @return 是否获取成功
     */
    boolean tryLock(String key, String value, long ttlSeconds);

    /**
     * 释放 Redis 分布式锁。
     * <p>
     * 只有锁值匹配时才允许删除，避免误删其他请求持有的锁。
     *
     * @param key   锁键
     * @param value 锁值
     */
    void unlock(String key, String value);
}
