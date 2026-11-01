package com.scott.payment.gateway.config;

import java.time.Duration;

/**
 * Gateway 下游 HTTP 超时启动门禁，确保连接和响应等待时间已显式配置且单项不超过平台 60 秒上限。
 */
public final class GatewayHttpClientTimeoutPolicy {

    /**
     * 最大超时毫秒数常量，统一 {@code GatewayHttpClientTimeoutPolicy} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * </p>
     */
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
