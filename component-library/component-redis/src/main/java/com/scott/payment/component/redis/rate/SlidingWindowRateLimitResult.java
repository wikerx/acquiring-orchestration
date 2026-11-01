package com.scott.payment.component.redis.rate;

/**
 * 分布式滑动窗口判定结果，包含拒绝后最早可重试秒数
 *
 * @param allowed 是否允许本次请求
 * @param retryAfterSeconds 拒绝时建议等待秒数，允许时为 0
 */
public record SlidingWindowRateLimitResult(boolean allowed, long retryAfterSeconds) {

    /**
     * 构造放行结果；放行请求不需要客户端等待。
     *
     * @return 重试等待时间为零的放行结果
     */
    public static SlidingWindowRateLimitResult permitted() {
        return new SlidingWindowRateLimitResult(true, 0L);
    }

    /**
     * 构造限流拒绝结果，并规范化客户端可使用的最短重试等待时间。
     *
     * @param retryAfterSeconds 拒绝后建议等待的秒数
     * @return 携带重试时间的拒绝结果
     */
    public static SlidingWindowRateLimitResult rejected(long retryAfterSeconds) {
        return new SlidingWindowRateLimitResult(false, Math.max(1L, retryAfterSeconds));
    }
}
