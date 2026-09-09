package com.scott.payment.component.redis.config;

import com.scott.payment.component.redis.lock.DistributedLockService;
import com.scott.payment.component.redis.lock.impl.RedissonDistributedLockService;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import io.lettuce.core.ReadFrom;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.ConstantDelay;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisClusterAutoConfiguration
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 统一配置 Lettuce Master 读取策略和条件化 Redisson Cluster 客户端。
 * @status : create
 */
@AutoConfiguration
@ConditionalOnClass({RedissonClient.class, LettuceClientConfigurationBuilderCustomizer.class})
@EnableConfigurationProperties({RedisProperties.class, PaymentRedissonProperties.class})
public class PaymentRedisClusterAutoConfiguration {

    /**
     * 强制 Lettuce 从 Master 读取支付、幂等和风控相关数据。
     *
     * @return Lettuce客户端配置定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "paymentRedisMasterReadCustomizer")
    public LettuceClientConfigurationBuilderCustomizer paymentRedisMasterReadCustomizer() {
        return builder -> builder.readFrom(ReadFrom.MASTER);
    }

    /**
     * 根据 Spring Redis Cluster 公共配置创建单个 RedissonClient。
     *
     * @param redisProperties    Spring Redis公共配置
     * @param redissonProperties Redisson池和线程配置
     * @return Redisson Cluster客户端
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnProperty(prefix = "payment.redis.redisson", name = "enabled", havingValue = "true")
    public RedissonClient redissonClient(RedisProperties redisProperties,
                                         PaymentRedissonProperties redissonProperties) {
        return Redisson.create(buildRedissonConfig(redisProperties, redissonProperties));
    }

    /**
     * 在 RedissonClient 存在时注册统一分布式锁入口。
     *
     * @param redissonClient Redisson客户端
     * @param metricsProvider Redis业务指标提供器
     * @return 分布式锁服务
     */
    @Bean
    @ConditionalOnMissingBean(DistributedLockService.class)
    @ConditionalOnProperty(prefix = "payment.redis.redisson", name = "enabled", havingValue = "true")
    public DistributedLockService distributedLockService(
            RedissonClient redissonClient,
            ObjectProvider<RedisBusinessMetrics> metricsProvider) {
        return new RedissonDistributedLockService(
                redissonClient,
                metricsProvider.getIfAvailable(RedisBusinessMetrics::noop)
        );
    }

    static Config buildRedissonConfig(RedisProperties redisProperties,
                                      PaymentRedissonProperties redissonProperties) {
        List<String> nodes = redisProperties.getCluster() == null
                ? List.of()
                : redisProperties.getCluster().getNodes();
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("Redisson requires spring.data.redis.cluster.nodes");
        }

        validatePoolBounds(redissonProperties);
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE)
                .setThreads(redissonProperties.getThreads())
                .setNettyThreads(redissonProperties.getNettyThreads())
                .setLockWatchdogTimeout(positiveMillis(
                        redissonProperties.getLockWatchdogTimeout(), "lockWatchdogTimeout"));

        ClusterServersConfig cluster = config.useClusterServers()
                .addNodeAddress(nodes.stream().map(PaymentRedisClusterAutoConfiguration::nodeAddress)
                        .toArray(String[]::new))
                .setScanInterval(positiveMillis(redissonProperties.getScanInterval(), "scanInterval"))
                .setCheckSlotsCoverage(redissonProperties.isCheckSlotsCoverage())
                .setConnectTimeout(durationMillis(redisProperties.getConnectTimeout(), Duration.ofSeconds(3)))
                .setTimeout(durationMillis(redisProperties.getTimeout(), Duration.ofSeconds(5)))
                .setRetryAttempts(redissonProperties.getRetryAttempts())
                .setRetryDelay(new ConstantDelay(redissonProperties.getRetryInterval()))
                .setMasterConnectionMinimumIdleSize(redissonProperties.getMasterConnectionMinimumIdleSize())
                .setMasterConnectionPoolSize(redissonProperties.getMasterConnectionPoolSize())
                .setSlaveConnectionMinimumIdleSize(redissonProperties.getSlaveConnectionMinimumIdleSize())
                .setSlaveConnectionPoolSize(redissonProperties.getSlaveConnectionPoolSize())
                .setSubscriptionConnectionMinimumIdleSize(
                        redissonProperties.getSubscriptionConnectionMinimumIdleSize())
                .setSubscriptionConnectionPoolSize(redissonProperties.getSubscriptionConnectionPoolSize())
                .setReadMode(ReadMode.MASTER)
                .setSubscriptionMode(SubscriptionMode.MASTER)
                .setKeepAlive(redissonProperties.isKeepAlive())
                .setTcpNoDelay(redissonProperties.isTcpNoDelay());

        if (StringUtils.hasText(redisProperties.getUsername())) {
            cluster.setUsername(redisProperties.getUsername());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            cluster.setPassword(redisProperties.getPassword());
        }
        return config;
    }

    /**
     * 在创建客户端前校验线程数和连接池上下界，避免错误配置在运行期耗尽连接。
     *
     * @param properties Redisson Cluster 配置
     */
    private static void validatePoolBounds(PaymentRedissonProperties properties) {
        if (properties.getThreads() <= 0 || properties.getNettyThreads() <= 0
                || properties.getRetryAttempts() < 0
                || properties.getMasterConnectionMinimumIdleSize() < 0
                || properties.getMasterConnectionPoolSize()
                < properties.getMasterConnectionMinimumIdleSize()
                || properties.getSlaveConnectionMinimumIdleSize() < 0
                || properties.getSlaveConnectionPoolSize()
                < properties.getSlaveConnectionMinimumIdleSize()
                || properties.getSubscriptionConnectionMinimumIdleSize() < 0
                || properties.getSubscriptionConnectionPoolSize()
                < properties.getSubscriptionConnectionMinimumIdleSize()) {
            throw new IllegalStateException("Redisson thread or connection pool bounds are invalid");
        }
    }

    /**
     * 将未声明协议的 Cluster 节点标准化为 Redisson 接受的地址。
     *
     * @param node Redis 节点，格式为 host:port 或 redis(s) URI
     * @return 带 redis 或 rediss 协议的节点地址
     */
    private static String nodeAddress(String node) {
        if (!StringUtils.hasText(node)) {
            throw new IllegalStateException("Redis Cluster node must not be blank");
        }
        String address = node.trim();
        return address.startsWith("redis://") || address.startsWith("rediss://")
                ? address
                : "redis://" + address;
    }

    /**
     * 将可空超时转换为 Redisson 使用的正整数毫秒值。
     *
     * @param configured 显式配置的超时
     * @param fallback 未配置时使用的默认超时
     * @return 正整数毫秒值
     */
    private static int durationMillis(Duration configured, Duration fallback) {
        return positiveMillis(configured == null ? fallback : configured, "Redis timeout");
    }

    /**
     * 校验 Duration 能安全转换为 Redisson 的 int 毫秒参数。
     *
     * @param duration 待转换时长
     * @param label 异常信息中的配置项名称
     * @return 1 到 Integer.MAX_VALUE 之间的毫秒值
     */
    private static int positiveMillis(Duration duration, String label) {
        if (duration == null || duration.isNegative() || duration.isZero()
                || duration.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalStateException(label + " must be between 1ms and Integer.MAX_VALUE ms");
        }
        return (int) duration.toMillis();
    }
}
