package com.scott.payment.component.redis.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisEnvironmentGuardAutoConfigurationTests
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : Redis 环境前缀启动门禁测试，覆盖受保护环境、开发环境和跨环境误配置。
 * @status : create
 */
@Slf4j
class PaymentRedisEnvironmentGuardAutoConfigurationTests {

    /**
     * 仅装配环境前缀门禁的隔离测试上下文，不连接 Redis。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PaymentRedisEnvironmentGuardAutoConfiguration.class));

    /**
     * 未激活受保护环境时保留 local/dev 默认配置兼容性。
     */
    @Test
    void shouldAllowDefaultAndDevelopmentProfiles() {
        log.info("测试 Redis 开发环境门禁，关键输入: 默认 profile 与 dev profile");
        contextRunner.run(context -> assertThat(context).hasNotFailed());
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context).hasNotFailed());
        log.info("Redis 开发环境门禁测试完成，结果: local/dev 默认配置保持兼容");
    }

    /**
     * test 环境三个 Redis 前缀一致时允许启动。
     */
    @Test
    void shouldAllowMatchingProtectedEnvironmentPrefixes() {
        log.info("测试 Redis test 环境门禁，关键输入: 三类 Key 前缀均为 acquiring:test");
        protectedProfileRunner("test")
                .withPropertyValues(
                        "payment.redis.key-prefix=acquiring:test",
                        "payment.cache.redis.key-prefix=acquiring:test",
                        "payment.global-id.state-key=acquiring:test:global-id:state"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            PaymentRedisEnvironmentGuardAutoConfiguration.RedisEnvironmentPrefixGuard.class
                    );
                });
        log.info("Redis test 环境门禁测试完成，结果: 精确匹配时允许启动");
    }

    /**
     * test 环境不得复用 prod 的直接业务 Key 前缀。
     */
    @Test
    void shouldRejectCrossEnvironmentRedisKeyPrefix() {
        log.info("测试 Redis 业务 Key 跨环境，关键输入: test profile、prod 直连前缀");
        protectedProfileRunner("test")
                .withPropertyValues(
                        "payment.redis.key-prefix=acquiring:prod",
                        "payment.cache.redis.key-prefix=acquiring:test",
                        "payment.global-id.state-key=acquiring:test:global-id:state"
                )
                .run(context -> assertRootCauseContains(
                        context.getStartupFailure(),
                        "payment.redis.key-prefix must use acquiring:test"
                ));
        log.info("Redis 业务 Key 跨环境测试完成，结果: 启动失败");
    }

    /**
     * uat 环境不得复用其他环境的 Spring Cache 命名空间。
     */
    @Test
    void shouldRejectCrossEnvironmentCacheKeyPrefix() {
        log.info("测试 Spring Cache 跨环境，关键输入: uat profile、test Cache 前缀");
        protectedProfileRunner("uat")
                .withPropertyValues(
                        "payment.redis.key-prefix=acquiring:uat",
                        "payment.cache.redis.key-prefix=acquiring:test",
                        "payment.global-id.state-key=acquiring:uat:global-id:state"
                )
                .run(context -> assertRootCauseContains(
                        context.getStartupFailure(),
                        "payment.cache.redis.key-prefix must use acquiring:uat"
                ));
        log.info("Spring Cache 跨环境测试完成，结果: 启动失败");
    }

    /**
     * prod 环境全局编号状态 Key 必须位于 prod 命名空间。
     */
    @Test
    void shouldRejectCrossEnvironmentGlobalIdStateKey() {
        log.info("测试全局 ID 状态跨环境，关键输入: prod profile、uat state Key");
        protectedProfileRunner("prod")
                .withPropertyValues(
                        "payment.redis.key-prefix=acquiring:prod",
                        "payment.cache.redis.key-prefix=acquiring:prod",
                        "payment.global-id.state-key=acquiring:uat:global-id:state"
                )
                .run(context -> assertRootCauseContains(
                        context.getStartupFailure(),
                        "payment.global-id.state-key must use acquiring:prod:global-id:state"
                ));
        log.info("全局 ID 状态跨环境测试完成，结果: 启动失败");
    }

    private ApplicationContextRunner protectedProfileRunner(String profile) {
        return contextRunner.withInitializer(
                context -> context.getEnvironment().setActiveProfiles(profile)
        );
    }

    private void assertRootCauseContains(Throwable failure, String message) {
        assertThat(failure).isNotNull();
        Throwable rootCause = failure;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertThat(rootCause).hasMessageContaining(message);
    }
}
