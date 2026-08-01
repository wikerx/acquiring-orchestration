package com.scott.payment.component.redis.config;

import com.scott.payment.component.redis.hash.RedisHashService;
import com.scott.payment.component.redis.hash.impl.RedisHashServiceImpl;
import com.scott.payment.component.redis.list.RedisListService;
import com.scott.payment.component.redis.list.impl.RedisListServiceImpl;
import com.scott.payment.component.redis.set.RedisSetService;
import com.scott.payment.component.redis.set.impl.RedisSetServiceImpl;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.string.impl.RedisStringServiceImpl;
import com.scott.payment.component.redis.zset.RedisZSetService;
import com.scott.payment.component.redis.zset.impl.RedisZSetServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGenericServiceRegistrationTests
 * @date : 2026-07-30 18:45
 * @email : scott_x@163.com
 * @description : 验证通用 Redis 包装器默认只保留现有生产依赖的 String Bean，其余能力必须显式启用
 * @status : create
 */
@Slf4j
class RedisGenericServiceRegistrationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean("redisTemplate", RedisTemplate.class, () -> mock(RedisTemplate.class))
            .withUserConfiguration(
                    RedisHashServiceImpl.class,
                    RedisListServiceImpl.class,
                    RedisSetServiceImpl.class,
                    RedisStringServiceImpl.class,
                    RedisZSetServiceImpl.class
            );

    @Test
    void shouldRegisterOnlyStringServiceByDefault() {
        log.info("测试通用 Redis 默认注册，关键输入: 不提供 payment.redis.generic 开关");

        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisStringService.class);
            assertThat(context).doesNotHaveBean(RedisHashService.class);
            assertThat(context).doesNotHaveBean(RedisListService.class);
            assertThat(context).doesNotHaveBean(RedisSetService.class);
            assertThat(context).doesNotHaveBean(RedisZSetService.class);
        });

        log.info("通用 Redis 默认注册测试完成，结果: 仅 String Bean 可用");
    }

    @Test
    void shouldRegisterCollectionServicesOnlyAfterExplicitEnablement() {
        log.info("测试通用 Redis 显式启用，关键输入: Hash/List/Set/ZSet 四个开关为 true");

        contextRunner
                .withPropertyValues(
                        "payment.redis.generic.hash-enabled=true",
                        "payment.redis.generic.list-enabled=true",
                        "payment.redis.generic.set-enabled=true",
                        "payment.redis.generic.zset-enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisHashService.class);
                    assertThat(context).hasSingleBean(RedisListService.class);
                    assertThat(context).hasSingleBean(RedisSetService.class);
                    assertThat(context).hasSingleBean(RedisZSetService.class);
                });

        log.info("通用 Redis 显式启用测试完成，结果: 四类集合 Bean 按配置注册");
    }

    @Test
    void shouldAllowStringServiceToBeExplicitlyDisabled() {
        log.info("测试通用 String 关闭，关键输入: string-enabled=false");

        contextRunner
                .withPropertyValues("payment.redis.generic.string-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RedisStringService.class));

        log.info("通用 String 关闭测试完成，结果: RedisStringService 未注册");
    }
}
