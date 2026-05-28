package com.global.payment.component.redis.lock;

public interface RedisLockService {

    boolean tryLock(String key, String value, long ttlSeconds);

    void unlock(String key, String value);
}

