package com.scott.payment.component.redis.lock;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DistributedLockService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 分布式互斥锁统一入口，要求所有等待和租约都有明确边界。
 * @status : create
 */
public interface DistributedLockService {

    /**
     * 在限定等待时间内获取带固定租约的锁。
     *
     * @param key       锁 Key
     * @param waitTime  最大等待时间，允许为零
     * @param leaseTime 固定租约时间，必须为正数
     * @return 是否获取成功
     */
    boolean tryLock(String key, Duration waitTime, Duration leaseTime);

    /**
     * 在限定等待时间内获取由 Redisson watchdog 续期的锁。
     *
     * @param key      锁 Key
     * @param waitTime 最大等待时间，允许为零
     * @return 是否获取成功
     */
    boolean tryLockWithWatchdog(String key, Duration waitTime);

    /**
     * 获取带固定租约的锁，竞争失败时抛出明确异常。
     *
     * @param key       锁 Key
     * @param waitTime  最大等待时间
     * @param leaseTime 固定租约时间
     */
    void lock(String key, Duration waitTime, Duration leaseTime);

    /**
     * 仅在当前线程持有锁时释放。
     *
     * @param key 锁 Key
     */
    void unlock(String key);

    /**
     * 判断当前线程是否持有指定锁。
     *
     * @param key 锁 Key
     * @return 当前线程是否持有锁
     */
    boolean isHeldByCurrentThread(String key);

    /**
     * 在固定租约锁内执行短临界区，竞争失败时返回未获取结果。
     *
     * @param key       锁 Key
     * @param waitTime  最大等待时间
     * @param leaseTime 固定租约时间
     * @param action    临界区操作
     * @param <T>       返回值类型
     * @return 锁执行结果
     */
    <T> DistributedLockExecution<T> execute(String key,
                                            Duration waitTime,
                                            Duration leaseTime,
                                            Supplier<T> action);
}
