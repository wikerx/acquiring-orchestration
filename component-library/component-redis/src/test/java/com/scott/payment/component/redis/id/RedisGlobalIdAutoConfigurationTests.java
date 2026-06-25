package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.id.LocalGlobalIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisGlobalIdAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisGlobalIdAutoConfiguration.class);

    @Test
    void shouldCreateLocalGeneratorWhenModeIsLocal() {
        contextRunner
                .withPropertyValues("payment.global-id.mode=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalIdGenerator.class);
                    assertThat(context.getBean(GlobalIdGenerator.class)).isInstanceOf(LocalGlobalIdGenerator.class);
                });
    }

    @Test
    void shouldRejectLocalGeneratorWhenProfileIsProd() {
        contextRunner
                .withPropertyValues("payment.global-id.mode=local", "spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectInvalidMode() {
        contextRunner
                .withPropertyValues("payment.global-id.mode=invalid")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldCreateRedisGeneratorWhenModeIsRedis() {
        contextRunner
                .withBean(StringRedisTemplate.class, TestStringRedisTemplate::new)
                .withPropertyValues("payment.global-id.mode=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisServerTimeProvider.class);
                    assertThat(context).hasSingleBean(GlobalIdGenerator.class);
                    assertThat(context.getBean(GlobalIdGenerator.class)).isInstanceOf(RedisGlobalIdGenerator.class);
                });
    }

    private static class TestStringRedisTemplate extends StringRedisTemplate {

        @Override
        public void afterPropertiesSet() {
            // 测试自动配置装配关系，不需要真实 Redis 连接工厂。
        }
    }
}
