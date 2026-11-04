package com.scott.payment.openapi.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import com.scott.payment.component.web.internal.InternalHttpTimeoutPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * service-openapi 调用 service-payment 的客户端配置属性，维护固定调用方和内部服务签名密钥。
 */
@Data
@ConfigurationProperties(prefix = "openapi.payment-client")
public class PaymentClientProperties {

    /**
     * 建立 service-payment 连接的超时，单位毫秒。
     */
    private int connectTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_CONNECT_TIMEOUT_MILLIS;

    /**
     * 等待 service-payment 同步响应的超时，单位毫秒。
     */
    private int readTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_READ_TIMEOUT_MILLIS;

    /**
     * 支付内部客户端连接池允许同时持有的最大连接总数。
     */
    private int maxConnectionsTotal = 32;

    /**
     * 单个 service-payment 实例允许同时持有的最大连接数。
     */
    private int maxConnectionsPerRoute = 16;

    /**
     * 从连接池等待可用连接的最长时间，单位毫秒。
     */
    private int connectionRequestTimeoutMillis = 10_000;

    /**
     * 复用连接前执行存活校验的空闲阈值，单位毫秒。
     */
    private int validateAfterInactivityMillis = 5_000;

    /**
     * 池内连接的最长生命周期，单位毫秒。
     */
    private int connectionTimeToLiveMillis = 300_000;

    /**
     * 后台清理空闲连接的时间阈值，单位毫秒。
     */
    private int idleConnectionEvictMillis = 30_000;

    /**
     * 内部服务调用方标识，用于 service-payment 审计调用来源。
     */
    private String internalCaller = "service-openapi";

    /**
     * 调用 service-payment 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret;

    /**
     * 校验固定调用方和 Nacos 注入的 active 密钥，禁止无密钥启动交易入口。
     */
    public void validate() {
        InternalServiceClientCredentialValidator.validate(
                "openapi payment-client", "service-openapi", internalCaller, internalSecret);
        InternalHttpTimeoutPolicy.validate(
                "openapi payment-client", connectTimeoutMillis, readTimeoutMillis);
        validateConnectionPool();
    }

    /**
     * 校验连接池容量和连接生命周期，防止配置错误在高并发时转化为无界排队或连接泄漏。
     */
    private void validateConnectionPool() {
        if (maxConnectionsTotal <= 0 || maxConnectionsTotal > 2_048) {
            throw new IllegalStateException(
                    "openapi payment-client max connections total must be between 1 and 2048");
        }
        if (maxConnectionsPerRoute <= 0 || maxConnectionsPerRoute > maxConnectionsTotal) {
            throw new IllegalStateException(
                    "openapi payment-client max connections per route must be between 1 and max connections total");
        }
        if (connectionRequestTimeoutMillis <= 0 || connectionRequestTimeoutMillis > 60_000) {
            throw new IllegalStateException(
                    "openapi payment-client connection request timeout must be between 1ms and 60000ms");
        }
        if (validateAfterInactivityMillis <= 0 || validateAfterInactivityMillis > 60_000) {
            throw new IllegalStateException(
                    "openapi payment-client validate-after-inactivity must be between 1ms and 60000ms");
        }
        if (connectionTimeToLiveMillis < 1_000 || connectionTimeToLiveMillis > 3_600_000) {
            throw new IllegalStateException(
                    "openapi payment-client connection time-to-live must be between 1000ms and 3600000ms");
        }
        if (idleConnectionEvictMillis < 1_000 || idleConnectionEvictMillis > connectionTimeToLiveMillis) {
            throw new IllegalStateException(
                    "openapi payment-client idle connection eviction must be between 1000ms and connection time-to-live");
        }
    }
}
