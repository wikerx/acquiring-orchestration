package com.global.payment.component.redis.cache;

public interface CacheService {

    void set(String key, String value, long ttlSeconds);

    String get(String key);
}

