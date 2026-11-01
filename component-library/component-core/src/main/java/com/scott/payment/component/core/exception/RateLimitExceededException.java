package com.scott.payment.component.core.exception;

import com.scott.payment.component.core.enums.ApiResultEnum;

/**
 * 对外请求超过滑动窗口配额时抛出的标准异常，携带 Retry-After 秒数
 */
public class RateLimitExceededException extends ApiException {

    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    /**
     * 创建限流异常并保证重试等待时间至少为一秒。
     *
     * @param retryAfterSeconds 建议客户端等待的秒数
     */
    public RateLimitExceededException(long retryAfterSeconds) {
        super(ApiResultEnum.TOO_MANY_REQUESTS.getCode(),
                "Too many requests. Please retry later.");
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    /**
     * 获取建议客户端等待的秒数，用于生成 HTTP Retry-After 响应头。
     *
     * @return 不小于一秒的等待时间
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
