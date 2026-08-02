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

    /** 是否启用 Redisson Cluster 客户端及统一分布式锁服务。 */
    private boolean enabled;
    /** Redis Cluster 拓扑刷新间隔。 */
    private Duration scanInterval = Duration.ofSeconds(5);
    /** 单次 Redis 操作失败后的最大重试次数。 */
    private int retryAttempts = 3;
    /** 相邻 Redis 重试之间的固定等待时间。 */
    private Duration retryInterval = Duration.ofSeconds(1);
    /** Master 连接池保持的最小空闲连接数。 */
    private int masterConnectionMinimumIdleSize = 2;
    /** 单个 Master 节点的最大连接数。 */
    private int masterConnectionPoolSize = 16;
    /** Replica 连接池保持的最小空闲连接数；支付读固定走 Master 时允许为零。 */
    private int slaveConnectionMinimumIdleSize;
    /** 单个 Replica 节点的最大连接数。 */
    private int slaveConnectionPoolSize = 8;
    /** Pub/Sub 连接池保持的最小空闲连接数。 */
    private int subscriptionConnectionMinimumIdleSize = 1;
    /** Pub/Sub 使用的最大连接数。 */
    private int subscriptionConnectionPoolSize = 4;
    /** Redisson 编解码和监听任务使用的工作线程数。 */
    private int threads = 4;
    /** Redisson Netty 网络事件线程数。 */
    private int nettyThreads = 4;
    /** 未指定固定租约时由看门狗续期的锁超时时间。 */
    private Duration lockWatchdogTimeout = Duration.ofSeconds(30);
    /** 是否要求启动时校验 Cluster 的全部槽位已覆盖。 */
    private boolean checkSlotsCoverage = true;
    /** 是否为 Redis TCP 连接启用保活。 */
    private boolean keepAlive = true;
    /** 是否关闭 Nagle 算法以降低短 Redis 命令延迟。 */
    private boolean tcpNoDelay = true;
}
