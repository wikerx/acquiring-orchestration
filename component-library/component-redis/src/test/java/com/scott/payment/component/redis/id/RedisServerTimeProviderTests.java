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
 * @description : Redis Server Time Provider Tests 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
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
         * current Millis，用于保存 Time String Redis Template 中与 currentmillis 相关的业务属性。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final Long currentMillis;

        /**
         * failure，用于保存 Time String Redis Template 中与 failure 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
