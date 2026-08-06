package com.scott.payment.component.redis.lock;

/**
 * 等待分布式锁时线程被中断，调用线程的中断标记已恢复。
 */
public class DistributedLockInterruptedException extends RuntimeException {

    /**
     * 创建锁等待中断异常；抛出前调用方已恢复当前线程中断标记。
     *
     * @param message 非敏感中断原因
     * @param cause Redisson 锁等待抛出的中断异常
     */
    public DistributedLockInterruptedException(String message, InterruptedException cause) {
        super(message, cause);
    }
}
