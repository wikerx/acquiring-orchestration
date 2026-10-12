package com.scott.payment.component.core.exception;

import com.scott.payment.component.core.enums.ApiResultEnum;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RateLimitExceededException
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 对外请求超过滑动窗口配额时抛出的标准异常，携带 Retry-After 秒数
 * @status : create
 */
public class RateLimitExceededException extends ApiException {

    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ApiResultEnum.TOO_MANY_REQUESTS.getCode(),
                "Too many requests. Please retry later.");
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
