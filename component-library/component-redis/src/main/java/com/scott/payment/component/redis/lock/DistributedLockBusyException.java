package com.scott.payment.component.redis.lock;

/**
 * 分布式锁在限定等待时间内未获取成功。
 */
public class DistributedLockBusyException extends RuntimeException {

    public DistributedLockBusyException(String message) {
        super(message);
    }
}
