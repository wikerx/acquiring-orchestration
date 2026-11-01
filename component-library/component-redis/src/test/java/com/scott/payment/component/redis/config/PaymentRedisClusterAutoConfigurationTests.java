package com.scott.payment.component.redis.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Lettuce Master 读取策略和条件化 Redisson Cluster 客户端装配。
 */
class PaymentRedisClusterAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PaymentRedisClusterAutoConfiguration.class));

    @Test
    void shouldKeepRedissonDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("redissonClient");
            assertThat(context).hasSingleBean(
                    org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer.class);
        });
    }

    @Test
    void shouldRejectRedissonWithoutClusterNodes() {
        contextRunner
                .withPropertyValues("payment.redis.redisson.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "Redisson requires spring.data.redis.cluster.nodes");
                });
    }

    @Test
    void shouldBuildClusterConfigFromSpringRedisProperties() throws Exception {
        RedisProperties redisProperties = new RedisProperties();
        RedisProperties.Cluster cluster = new RedisProperties.Cluster();
        cluster.setNodes(List.of("127.0.0.1:7001", "redis://127.0.0.1:7002"));
        redisProperties.setCluster(cluster);
        redisProperties.setPassword("secret");
        redisProperties.setConnectTimeout(Duration.ofSeconds(3));
        redisProperties.setTimeout(Duration.ofSeconds(5));

        Config config = PaymentRedisClusterAutoConfiguration.buildRedissonConfig(
                redisProperties, new PaymentRedissonProperties());

        assertThat(config.isClusterConfig()).isTrue();
        assertThat(config.getThreads()).isEqualTo(4);
        assertThat(config.getNettyThreads()).isEqualTo(4);
        assertThat(config.toYAML())
                .contains("redis://127.0.0.1:7001")
                .contains("redis://127.0.0.1:7002")
                .contains("password: \"secret\"")
                .contains("connectTimeout: 3000")
                .contains("timeout: 5000")
                .contains("readMode: \"MASTER\"")
                .contains("subscriptionMode: \"MASTER\"");
    }
}
