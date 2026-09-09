package com.scott.payment.payment.service.impl;

import com.scott.payment.component.redis.lock.DistributedLockBusyException;
import com.scott.payment.component.redis.lock.DistributedLockExecution;
import com.scott.payment.component.redis.lock.DistributedLockService;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AlwaysAvailableDistributedLockService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 支付服务单元测试使用的进程内锁替身，只用于构造不关注锁竞争的业务场景。
 * @status : create
 */
class AlwaysAvailableDistributedLockService implements DistributedLockService {

    /**
     * 模拟立即获得固定租约锁。
     *
     * @param key 测试锁 Key
     * @param waitTime 测试等待时间
     * @param leaseTime 测试固定租约
     * @return 固定返回 true
     */
    @Override
    public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
        return true;
    }

    /**
     * 模拟立即获得看门狗续期锁。
     *
     * @param key 测试锁 Key
     * @param waitTime 测试等待时间
     * @return 固定返回 true
     */
    @Override
    public boolean tryLockWithWatchdog(String key, Duration waitTime) {
        return true;
    }

    /**
     * 模拟固定租约锁获取；保留真实接口的竞争异常契约。
     *
     * @param key 测试锁 Key
     * @param waitTime 测试等待时间
     * @param leaseTime 测试固定租约
     */
    @Override
    public void lock(String key, Duration waitTime, Duration leaseTime) {
        if (!tryLock(key, waitTime, leaseTime)) {
            throw new DistributedLockBusyException("test lock is busy");
        }
    }

    /**
     * 模拟幂等释放，不维护测试锁状态。
     *
     * @param key 测试锁 Key
     */
    @Override
    public void unlock(String key) {
    }

    /**
     * 模拟当前线程始终持有测试锁。
     *
     * @param key 测试锁 Key
     * @return 固定返回 true
     */
    @Override
    public boolean isHeldByCurrentThread(String key) {
        return true;
    }

    @Override
    public <T> DistributedLockExecution<T> execute(String key,
                                                   Duration waitTime,
                                                   Duration leaseTime,
                                                   Supplier<T> action) {
        if (!tryLock(key, waitTime, leaseTime)) {
            return DistributedLockExecution.contended();
        }
        try {
            return DistributedLockExecution.acquired(action.get());
        } finally {
            unlock(key);
        }
    }
}
