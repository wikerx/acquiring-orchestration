package com.scott.payment.component.redis.lock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DistributedLockExecution
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 分布式锁执行结果，区分锁竞争与临界区返回空值。
 * @status : create
 *
 *
 * @param acquired 是否获取锁
 * @param value    临界区返回值
 * @param <T>      返回值类型
 */
public record DistributedLockExecution<T>(boolean acquired, T value) {

    /**
     * 创建成功执行结果。
     *
     * @param value 临界区返回值
     * @param <T>   返回值类型
     * @return 成功执行结果
     */
    public static <T> DistributedLockExecution<T> acquired(T value) {
        return new DistributedLockExecution<>(true, value);
    }

    /**
     * 创建锁竞争结果。
     *
     * @param <T> 返回值类型
     * @return 未获取锁结果
     */
    public static <T> DistributedLockExecution<T> contended() {
        return new DistributedLockExecution<>(false, null);
    }
}
