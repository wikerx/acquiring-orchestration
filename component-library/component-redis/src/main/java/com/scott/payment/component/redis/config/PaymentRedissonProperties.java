package com.scott.payment.component.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redisson Cluster 的线程、拓扑刷新和连接池边界配置。
 */
@Data
@ConfigurationProperties(prefix = "payment.redis.redisson")
public class PaymentRedissonProperties {

    private boolean enabled;
    private Duration scanInterval = Duration.ofSeconds(5);
    private int retryAttempts = 3;
    private Duration retryInterval = Duration.ofSeconds(1);
    private int masterConnectionMinimumIdleSize = 2;
    private int masterConnectionPoolSize = 16;
    private int slaveConnectionMinimumIdleSize;
    private int slaveConnectionPoolSize = 8;
    private int subscriptionConnectionMinimumIdleSize = 1;
    private int subscriptionConnectionPoolSize = 4;
    private int threads = 4;
    private int nettyThreads = 4;
    private Duration lockWatchdogTimeout = Duration.ofSeconds(30);
    private boolean checkSlotsCoverage = true;
    private boolean keepAlive = true;
    private boolean tcpNoDelay = true;
}
