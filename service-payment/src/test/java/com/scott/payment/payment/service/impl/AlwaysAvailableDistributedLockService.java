package com.scott.payment.payment.service.impl;

import com.scott.payment.component.redis.lock.DistributedLockBusyException;
import com.scott.payment.component.redis.lock.DistributedLockExecution;
import com.scott.payment.component.redis.lock.DistributedLockService;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 支付服务单元测试使用的进程内锁替身，只用于构造不关注锁竞争的业务场景。
 */
class AlwaysAvailableDistributedLockService implements DistributedLockService {

    @Override
    public boolean tryLock(String key, Duration waitTime, Duration leaseTime) {
        return true;
    }

    @Override
    public boolean tryLockWithWatchdog(String key, Duration waitTime) {
        return true;
    }

    @Override
    public void lock(String key, Duration waitTime, Duration leaseTime) {
        if (!tryLock(key, waitTime, leaseTime)) {
            throw new DistributedLockBusyException("test lock is busy");
        }
    }

    @Override
    public void unlock(String key) {
    }

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
