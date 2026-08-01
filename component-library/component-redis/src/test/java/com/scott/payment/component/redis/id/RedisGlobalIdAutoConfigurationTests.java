package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.id.LocalGlobalIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdAutoConfigurationTests
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : 验证全局 ID 自动装配的模式、受保护环境 Key 隔离和状态恢复启动门禁
 * @status : create
 */
@Slf4j
class RedisGlobalIdAutoConfigurationTests {

    /**
     * 隔离运行自动配置的测试上下文，不连接真实 Redis 或外部配置中心。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisGlobalIdAutoConfiguration.class);

    @Test
    void shouldCreateLocalGeneratorWhenModeIsLocal() {
        log.info("测试全局 ID local 装配，关键输入: 本地模式、无受保护 profile");
        contextRunner
                .withPropertyValues("payment.global-id.mode=local")
                .run(context -> {
                    assertThat(context).hasSingleBean(GlobalIdGenerator.class);
                    assertThat(context.getBean(GlobalIdGenerator.class)).isInstanceOf(LocalGlobalIdGenerator.class);
                });
        log.info("全局 ID local 装配测试完成，结果: 注册单 JVM 生成器");
    }

    @Test
    void shouldRejectLocalGeneratorWhenProfileIsProd() {
        log.info("测试全局 ID 生产模式门禁，关键输入: prod profile、local 模式");
        contextRunner
                .withPropertyValues("payment.global-id.mode=local", "spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
        log.info("全局 ID 生产模式门禁测试完成，结果: 启动失败");
    }

    @Test
    void shouldRejectInvalidMode() {
        log.info("测试全局 ID 模式枚举，关键输入: invalid");
        contextRunner
                .withPropertyValues("payment.global-id.mode=invalid")
                .run(context -> assertThat(context).hasFailed());
        log.info("全局 ID 模式枚举测试完成，结果: 启动失败");
    }

    @Test
    void shouldCreateRedisGeneratorWhenModeIsRedis() {
        log.info("测试全局 ID Redis 装配，关键输入: local 前缀与匹配的默认 state Key");
        contextRunner
                .withBean(StringRedisTemplate.class, TestStringRedisTemplate::new)
                .withPropertyValues("payment.global-id.mode=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisServerTimeProvider.class);
                    assertThat(context).hasSingleBean(GlobalIdGenerator.class);
                    assertThat(context.getBean(GlobalIdGenerator.class)).isInstanceOf(RedisGlobalIdGenerator.class);
                });
        log.info("全局 ID Redis 装配测试完成，结果: Redis TIME 与生成器 Bean 均已注册");
    }

    @Test
    void shouldRejectStateKeyThatDoesNotMatchEnvironmentPrefix() {
        log.info("测试全局 ID 环境隔离，关键输入: prod 前缀、dev 状态 Key");
        contextRunner
                .withBean(StringRedisTemplate.class, TestStringRedisTemplate::new)
                .withPropertyValues(
                        "payment.global-id.mode=redis",
                        "payment.redis.key-prefix=acquiring:prod",
                        "payment.global-id.state-key=acquiring:dev:global-id:state",
                        "spring.profiles.active=prod"
                )
                .run(context -> assertThat(context).hasFailed());
        log.info("全局 ID 环境隔离测试完成，结果: 跨环境状态 Key 阻断启动");
    }

    @Test
    void shouldRejectRestoreFloorWithoutAcknowledgement() {
        log.info("测试全局 ID 恢复确认门禁，关键输入: 正数时间下限、确认标识 false");
        contextRunner
                .withBean(StringRedisTemplate.class, TestStringRedisTemplate::new)
                .withPropertyValues(
                        "payment.global-id.mode=redis",
                        "payment.global-id.restore-floor-epoch-millis=1782295878123"
                )
                .run(context -> assertThat(context).hasFailed());
        log.info("全局 ID 恢复确认门禁测试完成，结果: 启动失败");
    }

    @Test
    void shouldAllowAcknowledgedRestoreWithPositiveFloor() {
        log.info("测试全局 ID 受控恢复装配，关键输入: 确认标识 true、正数时间下限");
        contextRunner
                .withBean(StringRedisTemplate.class, TestStringRedisTemplate::new)
                .withPropertyValues(
                        "payment.global-id.mode=redis",
                        "payment.global-id.restore-acknowledged=true",
                        "payment.global-id.restore-floor-epoch-millis=1782295878123"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GlobalIdGenerator.class);
                });
        log.info("全局 ID 受控恢复装配测试完成，结果: 配置成对时允许启动");
    }

    private static class TestStringRedisTemplate extends StringRedisTemplate {

        /**
         * 跳过连接工厂初始化；当前上下文测试只验证全局 ID 装配门禁，不执行 Redis 命令。
         */
        @Override
        public void afterPropertiesSet() {
            // 本测试只验证 Bean 门禁，不执行 Redis 命令，因此不装配连接工厂。
        }
    }
}
