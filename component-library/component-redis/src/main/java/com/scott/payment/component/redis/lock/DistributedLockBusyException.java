package com.scott.payment.component.redis.lock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DistributedLockBusyException
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 分布式锁在限定等待时间内未获取成功。
 * @status : create
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
