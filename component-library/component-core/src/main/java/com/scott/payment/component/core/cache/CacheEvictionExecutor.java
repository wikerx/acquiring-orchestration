package com.scott.payment.component.core.cache;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CacheEvictionExecutor
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 跨服务可靠缓存失效使用的精确删除契约，隔离数据库 Outbox 协调层与具体 Redis Cache 实现
 * @status : create
 */
public interface CacheEvictionExecutor {

    /**
     * 立即删除已登记 Cache 的指定业务 Key。
     *
     * @param cacheName Spring Cache 名称
     * @param businessKey 经过业务层校验的精确缓存键
     */
    void evict(String cacheName, String businessKey);
}
