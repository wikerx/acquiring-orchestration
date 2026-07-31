package com.scott.payment.component.redis.idempotent;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IdempotentAcquireResult
 * @date : 2026-07-30 18:20
 * @email : scott_x@163.com
 * @description : 表达 MQ 辅助幂等层的获取结果，区分 Redis 命中重复与 Redis 不可用后的数据库兜底路径
 * @status : create
 */
public enum IdempotentAcquireResult {

    /**
     * Redis 已原子登记当前业务摘要，消费失败时允许释放该占用以便 MQ 重试。
     */
    ACQUIRED,

    /**
     * Redis 在有效窗口内已存在相同业务摘要，消费者应停止重复副作用。
     */
    DUPLICATE,

    /**
     * Redis 缺失、异常或达到容量上限，消费者必须继续到数据库唯一约束，不能把该结果当作已获取 Redis 占用。
     */
    FALLBACK
}
