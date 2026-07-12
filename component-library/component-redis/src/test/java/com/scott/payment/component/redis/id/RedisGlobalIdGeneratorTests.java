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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Global Id Generator Tests，位于 component-library/component-redis 的测试层，用于承载该模块对应的业务职责和数据流转边界。
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
         * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private final long currentMillis;

        FixedRedisServerTimeProvider(long currentMillis) {
            super(null);
            this.currentMillis = currentMillis;
        }

        /**
         * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
         * @return 处理后的业务结果或页面展示数据。
         */
        @Override
        public long currentTimeMillis() {
            return currentMillis;
        }
    }

    private static class ScriptStringRedisTemplate extends StringRedisTemplate {

        private final Queue<List<?>> scriptResults = new ArrayDeque<>();

        /**
         * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private RuntimeException failure;

        /**
         * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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

        /**
         * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
         * @param script 请求参数或业务处理上下文，不能为空时由上层校验约束。
         * @param keys 请求参数或业务处理上下文，不能为空时由上层校验约束。
         * @param args 请求参数或业务处理上下文，不能为空时由上层校验约束。
         * @return 处理后的业务结果或页面展示数据。
         */
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
