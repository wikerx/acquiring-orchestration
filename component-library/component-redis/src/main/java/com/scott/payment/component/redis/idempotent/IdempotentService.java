package com.scott.payment.component.redis.idempotent;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IdempotentService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 提供 Redis 辅助幂等能力；Redis 只负责快速去重，资金或持久化副作用仍必须由数据库唯一约束兜底
 * @status : create
 */
public interface IdempotentService {

    /**
     * 获取幂等处理权。
     * <p>
     * 返回 true 表示当前请求第一次进入，可继续处理；返回 false 表示相同业务键仍在有效期内，应直接拦截或返回已处理结果。
     *
     * @param idempotentKey 幂等业务键
     * @param ttlSeconds    幂等有效期，单位秒
     * @return 是否获取成功
     */
    boolean acquire(String idempotentKey, long ttlSeconds);

    /**
     * 在指定业务命名空间内获取幂等处理权。
     *
     * <p>实现可使用低基数 Redis 结构保存业务键摘要，避免每条 MQ 消息创建一个物理 Key。</p>
     *
     * @param namespace    幂等业务命名空间
     * @param businessKey  业务幂等键
     * @param ttlSeconds   幂等有效期，单位秒
     * @return 是否获取成功
     */
    default boolean acquire(String namespace, String businessKey, long ttlSeconds) {
        return acquire(namespace + ":" + businessKey, ttlSeconds);
    }

    /**
     * 获取 MQ 消费的辅助幂等处理权，并显式返回 Redis 降级状态。
     *
     * <p>默认实现兼容既有布尔接口；Redis 实现必须覆盖本方法，确保基础设施异常返回
     * {@link IdempotentAcquireResult#FALLBACK}，由消费者继续访问数据库唯一约束。</p>
     *
     * @param namespace   低基数消费业务命名空间，不得包含消息明文或敏感业务值
     * @param businessKey 消息业务幂等键，只允许以摘要形式写入 Redis
     * @param ttlSeconds  去重有效期，单位秒
     * @return 获取成功、命中重复或降级数据库兜底
     */
    default IdempotentAcquireResult acquireMq(String namespace, String businessKey, long ttlSeconds) {
        return acquire(namespace, businessKey, ttlSeconds)
                ? IdempotentAcquireResult.ACQUIRED
                : IdempotentAcquireResult.DUPLICATE;
    }

    /**
     * 释放尚未完成的幂等处理权。
     * <p>
     * 仅在业务处理失败、允许上游安全重试时调用；业务成功后不得释放。
     *
     * @param idempotentKey 幂等业务键
     */
    default void release(String idempotentKey) {
        // 默认实现不持有外部资源；具体 Redis 实现仅释放尚未完成的处理权。
    }

    /**
     * 释放指定业务命名空间内尚未完成的幂等处理权。
     *
     * @param namespace   幂等业务命名空间
     * @param businessKey 业务幂等键
     */
    default void release(String namespace, String businessKey) {
        release(namespace + ":" + businessKey);
    }

    /**
     * 释放指定 MQ 命名空间中尚未完成的辅助幂等占用。
     *
     * <p>TTL 用于定位当前时间桶和前一时间桶；只有获取结果为
     * {@link IdempotentAcquireResult#ACQUIRED} 且业务处理失败时才能调用。</p>
     *
     * @param namespace   MQ 消费业务命名空间
     * @param businessKey 消息业务幂等键
     * @param ttlSeconds  获取处理权时使用的去重有效期，单位秒
     */
    default void releaseMq(String namespace, String businessKey, long ttlSeconds) {
        release(namespace, businessKey);
    }
}
