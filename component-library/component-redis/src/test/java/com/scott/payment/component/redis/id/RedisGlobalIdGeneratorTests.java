package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdConstants;
import com.scott.payment.component.core.id.GlobalIdValidator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdGeneratorTests
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : RedisGlobalIdGeneratorTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class RedisGlobalIdGeneratorTests {

    @Test
    void nextIdShouldReturnValidGlobalId() {
        ScriptStringRedisTemplate redisTemplate = new ScriptStringRedisTemplate();
        redisTemplate.addScriptResult(List.of(1782286218123L, 45L, 0L));
        RedisGlobalIdGenerator generator = new RedisGlobalIdGenerator(
                redisTemplate,
                new FixedRedisServerTimeProvider(1782286218123L),
                new RedisGlobalIdProperties()
        );

        String id = generator.nextId();

        assertThat(id).hasSize(GlobalIdConstants.ID_LENGTH);
        assertThat(id).containsOnlyDigits();
        assertThat(GlobalIdValidator.isValid(id)).isTrue();
        assertThat(id).isEqualTo("2606241530181230000458");
    }

    @Test
    void nextIdShouldThrowWhenRedisScriptFails() {
        ScriptStringRedisTemplate redisTemplate = new ScriptStringRedisTemplate();
        redisTemplate.throwOnExecute(new IllegalStateException("redis down"));
        RedisGlobalIdGenerator generator = new RedisGlobalIdGenerator(
                redisTemplate,
                new FixedRedisServerTimeProvider(1782295818123L),
                new RedisGlobalIdProperties()
        );

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Redis 生成全局唯一标识失败")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void nextIdShouldRetryWhenSequenceOverflowOnce() {
        ScriptStringRedisTemplate redisTemplate = new ScriptStringRedisTemplate();
        redisTemplate.addScriptResult(List.of(1782295818123L, 1_000_000L, 1L));
        redisTemplate.addScriptResult(List.of(1782295818124L, 1L, 0L));
        RedisGlobalIdProperties properties = new RedisGlobalIdProperties();
        properties.setRetrySleepMillis(0L);
        RedisGlobalIdGenerator generator = new RedisGlobalIdGenerator(
                redisTemplate,
                new FixedRedisServerTimeProvider(1782295818123L),
                properties
        );

        String id = generator.nextId();

        assertThat(GlobalIdValidator.isValid(id)).isTrue();
        assertThat(redisTemplate.getExecuteCount()).isEqualTo(2);
    }

    @Test
    void nextIdShouldThrowWhenSequenceOverflowExceedsRetryLimit() {
        ScriptStringRedisTemplate redisTemplate = new ScriptStringRedisTemplate();
        redisTemplate.addScriptResult(List.of(1782295818123L, 1_000_000L, 1L));
        redisTemplate.addScriptResult(List.of(1782295818124L, 1_000_001L, 1L));
        RedisGlobalIdProperties properties = new RedisGlobalIdProperties();
        properties.setMaxRetryTimes(1);
        properties.setRetrySleepMillis(0L);
        RedisGlobalIdGenerator generator = new RedisGlobalIdGenerator(
                redisTemplate,
                new FixedRedisServerTimeProvider(1782295818123L),
                properties
        );

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("全局唯一标识序列超过毫秒上限");
    }

    @Test
    void constructorShouldRejectInvalidTimezone() {
        RedisGlobalIdProperties properties = new RedisGlobalIdProperties();
        properties.setTimezone("invalid-zone");

        assertThatThrownBy(() -> new RedisGlobalIdGenerator(
                new ScriptStringRedisTemplate(),
                new FixedRedisServerTimeProvider(1782295818123L),
                properties
        ))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("全局唯一标识时区配置非法");
    }

    private static class FixedRedisServerTimeProvider extends RedisServerTimeProvider {

        /**
         * current Millis 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final long currentMillis;

        FixedRedisServerTimeProvider(long currentMillis) {
            super(null);
            this.currentMillis = currentMillis;
        }

        @Override
        /**
         * 完成 current Time Millis 分支的校验或转换，返回值供当前调用链继续组装结果。
         * <p>
         * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
         * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
         * </p>
         * @return 当前方法计算或转换后的业务结果
         */
        public long currentTimeMillis() {
            return currentMillis;
        }
    }

    private static class ScriptStringRedisTemplate extends StringRedisTemplate {

        /**
         * script Results 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private final Queue<List<?>> scriptResults = new ArrayDeque<>();

        /**
         * failure 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private RuntimeException failure;

        /**
         * execute Count 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
         * </p>
         */
        private int executeCount;

        void addScriptResult(List<?> result) {
            scriptResults.add(result);
        }

        void throwOnExecute(RuntimeException targetFailure) {
            this.failure = targetFailure;
        }

        int getExecuteCount() {
            return executeCount;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            executeCount++;
            if (failure != null) {
                throw failure;
            }
            return (T) scriptResults.remove();
        }
    }
}
