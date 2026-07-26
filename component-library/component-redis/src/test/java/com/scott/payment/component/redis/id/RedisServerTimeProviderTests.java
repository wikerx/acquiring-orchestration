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
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : RedisServerTimeProviderTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
         * current Millis 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final Long currentMillis;

        /**
         * failure 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final RuntimeException failure;

        TimeStringRedisTemplate(Long currentMillis, RuntimeException failure) {
            this.currentMillis = currentMillis;
            this.failure = failure;
        }

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
