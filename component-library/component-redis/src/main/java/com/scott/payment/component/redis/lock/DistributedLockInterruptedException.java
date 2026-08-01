package com.scott.payment.component.redis.lock;

/**
 * 等待分布式锁时线程被中断，调用线程的中断标记已恢复。
 */
public class DistributedLockInterruptedException extends RuntimeException {

    public DistributedLockInterruptedException(String message, InterruptedException cause) {
        super(message, cause);
    }
}
