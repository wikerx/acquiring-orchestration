package com.scott.payment.gateway.config;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayHttpClientTimeoutPolicy
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : Gateway 下游 HTTP 超时启动门禁，确保连接和响应等待时间已显式配置且单项不超过平台 60 秒上限。
 * @status : create
 */
public final class GatewayHttpClientTimeoutPolicy {

    private static final long MAX_TIMEOUT_MILLIS = 60_000L;

    private GatewayHttpClientTimeoutPolicy() {
    }

    /**
     * 校验 Gateway 实际使用的下游 HTTP 客户端超时配置。
     *
     * @param connectTimeoutMillis 建立下游 TCP 连接的最长等待时间，单位毫秒
     * @param responseTimeout 下游完整响应的最长等待时间
     * @throws IllegalStateException 任一超时缺失、非正数或超过 60 秒
     */
    public static void validate(Integer connectTimeoutMillis, Duration responseTimeout) {
        if (connectTimeoutMillis == null || connectTimeoutMillis <= 0) {
            throw new IllegalStateException("gateway HTTP connect timeout must be positive");
        }
        if (connectTimeoutMillis > MAX_TIMEOUT_MILLIS) {
            throw new IllegalStateException("gateway HTTP connect timeout must not exceed 60000ms");
        }
        if (responseTimeout == null || responseTimeout.isZero() || responseTimeout.isNegative()) {
            throw new IllegalStateException("gateway HTTP response timeout must be positive");
        }
        if (responseTimeout.compareTo(Duration.ofMillis(MAX_TIMEOUT_MILLIS)) > 0) {
            throw new IllegalStateException("gateway HTTP response timeout must not exceed 60000ms");
        }
    }
}
