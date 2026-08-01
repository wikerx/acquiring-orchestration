package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdConstants;
import com.scott.payment.component.core.id.GlobalIdValidator;
import lombok.extern.slf4j.Slf4j;
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
 * @description : 验证 Redis 全局 ID 的格式、重试、故障、状态 Key 与受控恢复时间下限
 * @status : create
 */
@Slf4j
class RedisGlobalIdGeneratorTests {

    @Test
    void nextIdShouldReturnValidGlobalId() {
        log.info("测试 Redis 全局 ID 正常生成，关键输入: 固定 Redis TIME、序列 45");
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
        log.info("Redis 全局 ID 正常生成测试完成，结果: 22 位纯数字且校验位有效");
    }

    @Test
    void nextIdShouldThrowWhenRedisScriptFails() {
        log.info("测试 Redis 全局 ID 故障边界，关键输入: Lua 执行抛出连接异常");
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
        log.info("Redis 全局 ID 故障边界测试完成，结果: 未降级到本地发号");
    }

    @Test
    void nextIdShouldRetryWhenSequenceOverflowOnce() {
        log.info("测试 Redis 全局 ID 序列重试，关键输入: 首次溢出、第二次序列为 1");
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
        log.info("Redis 全局 ID 序列重试测试完成，结果: 第二次 Lua 调用生成有效编号");
    }

    @Test
    void nextIdShouldThrowWhenSequenceOverflowExceedsRetryLimit() {
        log.info("测试 Redis 全局 ID 序列上限，关键输入: 连续两次超过单毫秒最大序列");
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
        log.info("Redis 全局 ID 序列上限测试完成，结果: 超过重试预算后明确失败");
    }

    @Test
    void constructorShouldRejectInvalidTimezone() {
        log.info("测试 Redis 全局 ID 时区配置，关键输入: 不存在的 IANA 时区");
        RedisGlobalIdProperties properties = new RedisGlobalIdProperties();
        properties.setTimezone("invalid-zone");

        assertThatThrownBy(() -> new RedisGlobalIdGenerator(
                new ScriptStringRedisTemplate(),
                new FixedRedisServerTimeProvider(1782295818123L),
                properties
        ))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("全局唯一标识时区配置非法");
        log.info("Redis 全局 ID 时区配置测试完成，结果: 构造阶段拒绝非法时区");
    }

    @Test
    void constructorShouldRejectNonStandardStateKey() {
        log.info("测试 Redis 全局 ID 状态 Key，关键输入: 带无必要 Hash Tag 的旧 Key");
        RedisGlobalIdProperties properties = new RedisGlobalIdProperties();
        properties.setStateKey("acquiring:dev:global-id:{state}");

        assertThatThrownBy(() -> new RedisGlobalIdGenerator(
                new ScriptStringRedisTemplate(),
                new FixedRedisServerTimeProvider(1_782_295_818_123L),
                properties
        ))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Redis 配置非法");
        log.info("Redis 全局 ID 状态 Key 测试完成，结果: 旧 Hash Tag 格式被拒绝");
    }

    @Test
    void nextIdShouldPassAcknowledgedRestoreFloorToLua() {
        log.info("测试 Redis 全局 ID 受控恢复，关键输入: 已确认恢复、时间下限高于当前 Redis TIME");
        long currentMillis = 1_782_295_818_123L;
        long restoreFloorMillis = currentMillis + 60_000L;
        ScriptStringRedisTemplate redisTemplate = new ScriptStringRedisTemplate();
        redisTemplate.addScriptResult(List.of(restoreFloorMillis, 1L, 0L));
        RedisGlobalIdProperties properties = new RedisGlobalIdProperties();
        properties.setRestoreAcknowledged(true);
        properties.setRestoreFloorEpochMillis(restoreFloorMillis);
        RedisGlobalIdGenerator generator = new RedisGlobalIdGenerator(
                redisTemplate,
                new FixedRedisServerTimeProvider(currentMillis),
                properties
        );

        String id = generator.nextId();

        assertThat(GlobalIdValidator.isValid(id)).isTrue();
        assertThat(redisTemplate.getLastArguments())
                .containsExactly(
                        String.valueOf(currentMillis),
                        String.valueOf(properties.getMaxSequence()),
                        String.valueOf(restoreFloorMillis)
                );
        log.info("Redis 全局 ID 受控恢复测试完成，结果: Lua 收到审核后的最小发号毫秒");
    }

    @Test
    void constructorShouldRejectUnacknowledgedRestoreFloor() {
        log.info("测试 Redis 全局 ID 恢复门禁，关键输入: 提供时间下限但未确认恢复");
        RedisGlobalIdProperties properties = new RedisGlobalIdProperties();
        properties.setRestoreFloorEpochMillis(1_782_295_878_123L);

        assertThatThrownBy(() -> new RedisGlobalIdGenerator(
                new ScriptStringRedisTemplate(),
                new FixedRedisServerTimeProvider(1_782_295_818_123L),
                properties
        ))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("状态恢复配置非法");
        log.info("Redis 全局 ID 恢复门禁测试完成，结果: 未确认的恢复下限被拒绝");
    }

    private static class FixedRedisServerTimeProvider extends RedisServerTimeProvider {

        /**
         * 测试用 Redis 服务端时间，单位 epochMillis；固定值用于稳定断言，不含业务数据。
         */
        private final long currentMillis;

        FixedRedisServerTimeProvider(long currentMillis) {
            super(null);
            this.currentMillis = currentMillis;
        }

        /**
         * 返回构造器指定的 Redis 服务端时间，避免全局 ID 测试依赖系统时钟。
         */
        @Override
        public long currentTimeMillis() {
            return currentMillis;
        }
    }

    private static class ScriptStringRedisTemplate extends StringRedisTemplate {

        /**
         * 按调用顺序返回的 Lua 结果队列，仅保存测试构造的时间、序列和溢出标识。
         */
        private final Queue<List<?>> scriptResults = new ArrayDeque<>();

        /**
         * 测试要求 Lua 调用抛出的异常；为空表示执行预置结果。
         */
        private RuntimeException failure;

        /**
         * Lua 执行次数，单位为次；用于验证序列溢出后的重试次数。
         */
        private int executeCount;

        /**
         * 最近一次 Lua 参数副本，不含业务敏感值；用于验证恢复时间下限已传入脚本。
         */
        private Object[] lastArguments;

        void addScriptResult(List<?> result) {
            scriptResults.add(result);
        }

        void throwOnExecute(RuntimeException targetFailure) {
            this.failure = targetFailure;
        }

        int getExecuteCount() {
            return executeCount;
        }

        Object[] getLastArguments() {
            return lastArguments == null ? new Object[0] : lastArguments.clone();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            executeCount++;
            lastArguments = args == null ? new Object[0] : args.clone();
            if (failure != null) {
                throw failure;
            }
            return (T) scriptResults.remove();
        }
    }
}
