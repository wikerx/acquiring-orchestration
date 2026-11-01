package com.scott.payment.component.core.exception;

import com.scott.payment.component.core.enums.ApiResultEnum;

/**
 * 对外请求超过滑动窗口配额时抛出的标准异常，携带 Retry-After 秒数
 */
public class RateLimitExceededException extends ApiException {

    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ApiResultEnum.TOO_MANY_REQUESTS.getCode(),
                "Too many requests. Please retry later.");
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    /**
     * 查询{@code getRetryAfterSeconds}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 公共组件库 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
