package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisServerTimeProviderTests
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Server Time Provider Tests，位于 component-library/component-redis 的测试层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
class RedisServerTimeProviderTests {

    @Test
    void currentTimeMillisShouldReturnRedisTime() {
        RedisServerTimeProvider provider = new RedisServerTimeProvider(new TimeStringRedisTemplate(1782295818123L, null));

        assertThat(provider.currentTimeMillis()).isEqualTo(1782295818123L);
    }

    @Test
    void currentTimeMillisShouldThrowWhenRedisTimeIsNull() {
        RedisServerTimeProvider provider = new RedisServerTimeProvider(new TimeStringRedisTemplate(null, null));

        assertThatThrownBy(provider::currentTimeMillis)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Redis TIME 获取失败");
    }

    @Test
    void currentTimeMillisShouldKeepCauseWhenRedisFails() {
        RedisServerTimeProvider provider = new RedisServerTimeProvider(
                new TimeStringRedisTemplate(null, new IllegalStateException("redis down"))
        );

        assertThatThrownBy(provider::currentTimeMillis)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Redis TIME 获取失败")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static class TimeStringRedisTemplate extends StringRedisTemplate {

        /**
         * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private final Long currentMillis;

        /**
         * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private final RuntimeException failure;

        TimeStringRedisTemplate(Long currentMillis, RuntimeException failure) {
            this.currentMillis = currentMillis;
            this.failure = failure;
        }

        /**
         * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
         * @param action 请求参数或业务处理上下文，不能为空时由上层校验约束。
         * @return 处理后的业务结果或页面展示数据。
         */
        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisCallback<T> action) {
            if (failure != null) {
                throw failure;
            }
            return (T) currentMillis;
        }
    }
}
