package com.scott.payment.component.redis.rate;

/**
 * Redis 限流基础设施不可用时的处理策略。
 */
public enum RateLimitFailurePolicy {

    /**
     * 降级到当前 JVM 的有界滑动窗口。集群故障期间各实例独立计数，最坏可放行约
     * {@code 单实例额度 * 实例数}，适用于开发、测试或明确接受该降级边界的环境。
     */
    LOCAL_FALLBACK,

    /**
     * Redis 判定失败时直接拒绝请求，保持全局额度不会因实例拆分而扩大。
     */
    FAIL_CLOSED
}
