package com.scott.payment.component.redis.rate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SlidingWindowRateLimitResult
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 分布式滑动窗口判定结果，包含拒绝后最早可重试秒数
 * @status : create
 *
 * @param allowed 是否允许本次请求
 * @param retryAfterSeconds 拒绝时建议等待秒数，允许时为 0
 */
public record SlidingWindowRateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static SlidingWindowRateLimitResult permitted() {
        return new SlidingWindowRateLimitResult(true, 0L);
    }

    public static SlidingWindowRateLimitResult rejected(long retryAfterSeconds) {
        return new SlidingWindowRateLimitResult(false, Math.max(1L, retryAfterSeconds));
    }
}
