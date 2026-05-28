package com.global.payment.component.redis.idempotent;

public interface IdempotentService {

    boolean acquire(String idempotentKey, long ttlSeconds);
}

