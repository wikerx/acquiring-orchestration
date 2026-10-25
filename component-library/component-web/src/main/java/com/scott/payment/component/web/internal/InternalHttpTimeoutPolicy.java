package com.scott.payment.component.web.internal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalHttpTimeoutPolicy
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : 微服务同步 HTTP 超时策略，位于共享 Web 基础层；统一默认值并阻止零、负数或超过平台上限的配置进入客户端。
 * @status : create
 */
public final class InternalHttpTimeoutPolicy {

    /** 默认建连超时；单位毫秒，非敏感且必须为正数。 */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3_000;

    /** 默认读取超时；单位毫秒，非敏感且必须为正数。 */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 30_000;

    /** 单次同步内部 HTTP 调用硬上限；单位毫秒，非敏感且必须为正数。 */
    public static final int MAX_TIMEOUT_MILLIS = 60_000;

    private InternalHttpTimeoutPolicy() {
    }

    /**
     * 校验建连和读取超时均为正数且不超过平台同步调用上限。
     *
     * @param clientName 客户端配置名称，仅用于非敏感启动错误定位
     * @param connectTimeoutMillis 建连超时，单位毫秒
     * @param readTimeoutMillis 读取超时，单位毫秒
     */
    public static void validate(String clientName, int connectTimeoutMillis, int readTimeoutMillis) {
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalStateException(clientName + " timeouts must be positive");
        }
        if (connectTimeoutMillis > MAX_TIMEOUT_MILLIS || readTimeoutMillis > MAX_TIMEOUT_MILLIS) {
            throw new IllegalStateException(clientName + " timeouts must not exceed 60000ms");
        }
    }
}
