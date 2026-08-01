package com.scott.payment.component.redis.failure;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.id.RedisGlobalIdGenerator;
import com.scott.payment.component.redis.id.RedisGlobalIdProperties;
import com.scott.payment.component.redis.id.RedisServerTimeProvider;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.impl.RedisIdempotentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisConnectionFailureIntegrationTests
 * @date : 2026-07-30 22:30
 * @email : scott_x@163.com
 * @description : 在显式启用时连接已确认无监听的端口，验证 MQ 降级和全局 ID 禁止本地降级
 * @status : create
 */
@Slf4j
@EnabledIfSystemProperty(named = "redis.failure.integration.enabled", matches = "true")
class RedisConnectionFailureIntegrationTests {

    /**
     * 指向不可达 Cluster 种子节点的 Lettuce 连接工厂，命令超时限制为 500 毫秒。
     */
    private LettuceConnectionFactory connectionFactory;

    /**
     * 指向故障端口的字符串 Redis 模板。
     */
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        String configuredNodes = System.getProperty(
                "redis.failure.cluster.nodes",
                "127.0.0.1:16379,127.0.0.1:16380"
        );
        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(
                Arrays.stream(configuredNodes.split(","))
                        .map(String::trim)
                        .filter(node -> !node.isEmpty())
                        .toList()
        );
        clusterConfiguration.setMaxRedirects(1);
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500L))
                .shutdownTimeout(Duration.ZERO)
                .build();
        connectionFactory = new LettuceConnectionFactory(
                clusterConfiguration,
                clientConfiguration
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /**
     * MQ 辅助去重在连接拒绝时必须返回 FALLBACK，把最终幂等交给数据库唯一约束。
     */
    @Test
    void shouldFallbackMqDedupWhenRedisConnectionIsRefused() {
        log.info("测试 Redis 连接拒绝下的 MQ 去重，关键输入: 本机已确认无监听端口");
        PaymentRedisProperties redisProperties = new PaymentRedisProperties();
        redisProperties.setKeyPrefix("acquiring:failure-it");
        RedisIdempotentServiceImpl idempotentService = new RedisIdempotentServiceImpl(
                provider(redisTemplate),
                redisProperties
        );

        IdempotentAcquireResult result =
                idempotentService.acquireMq("failure-drill", "MESSAGE-001", 60L);

        assertThat(result).isEqualTo(IdempotentAcquireResult.FALLBACK);
        log.info("Redis 连接拒绝 MQ 去重测试完成，结果: FALLBACK 到数据库最终幂等");
    }

    /**
     * 全局 ID 在连接拒绝时必须抛出业务异常，禁止切换到 JVM 本地序列。
     */
    @Test
    void shouldRejectGlobalIdGenerationWhenRedisConnectionIsRefused() {
        log.info("测试 Redis 连接拒绝下的全局 ID，关键输入: 本机已确认无监听端口");
        RedisGlobalIdProperties properties = new RedisGlobalIdProperties();
        properties.setStateKey("acquiring:failure-it:global-id:state");
        properties.setMaxRetryTimes(0);
        RedisGlobalIdGenerator generator = new RedisGlobalIdGenerator(
                redisTemplate,
                new RedisServerTimeProvider(redisTemplate),
                properties
        );

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Redis TIME");
        log.info("Redis 连接拒绝全局 ID 测试完成，结果: 明确失败且未生成本地编号");
    }

    /**
     * 把测试模板注册为 ObjectProvider，复用生产构造器的可选依赖语义。
     *
     * @param template 指向故障端口的字符串模板
     * @return 可选 Redis 模板提供器
     */
    private ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate template) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("stringRedisTemplate", template);
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
