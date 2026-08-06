package com.scott.payment.component.redis.lock;

/**
 * 分布式锁在限定等待时间内未获取成功。
 */
public class DistributedLockBusyException extends RuntimeException {

    /**
     * 创建锁竞争异常，不携带锁 Key，避免业务标识进入上层错误响应。
     *
     * @param message 可供调用方识别的非敏感原因
     */
    public DistributedLockBusyException(String message) {
        super(message);
    }
}
