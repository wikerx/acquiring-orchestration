package com.sinopay.payment.component.redis.idempotent;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IdempotentService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 幂等控制服务接口
 * @status : create
 */
public interface IdempotentService {

    boolean acquire(String idempotentKey, long ttlSeconds);
}

