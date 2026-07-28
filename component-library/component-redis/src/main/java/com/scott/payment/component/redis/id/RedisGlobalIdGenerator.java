package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdConstants;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.id.GlobalIdValidator;
import com.scott.payment.component.core.id.LuhnMod10Utils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdGenerator
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : Redis Global ID Generator 协作组件，位于 公共组件库，封装 redisglobalIDgenerator 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class RedisGlobalIdGenerator implements GlobalIdGenerator {

    /**
     * Redis 原子递增和防时间回拨脚本。
     */
    private static final DefaultRedisScript<List> SEQUENCE_SCRIPT = new DefaultRedisScript<>("""
            local lastMillisKey = KEYS[1]
            local seqKeyPrefix = ARGV[1]
            local currentMillis = tonumber(ARGV[2])
            local expireSeconds = tonumber(ARGV[3])
            local maxSequence = tonumber(ARGV[4])

            local lastMillisValue = redis.call('GET', lastMillisKey)
            local lastMillis = 0

            if lastMillisValue then
                lastMillis = tonumber(lastMillisValue)
            end

            local effectiveMillis = currentMillis

            if currentMillis < lastMillis then
                effectiveMillis = lastMillis
            else
                redis.call('SET', lastMillisKey, currentMillis)
            end

            local seqKey = seqKeyPrefix .. effectiveMillis
            local seq = redis.call('INCR', seqKey)

            if seq == 1 then
                redis.call('EXPIRE', seqKey, expireSeconds)
            end

            if seq > maxSequence then
                return {effectiveMillis, seq, 1}
            end

            return {effectiveMillis, seq, 0}
            """, List.class);

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Redis 服务端时间提供器。
     */
    private final RedisServerTimeProvider redisServerTimeProvider;

    /**
     * Redis 全局编号配置。
     */
    private final RedisGlobalIdProperties properties;

    /**
     * 编号时间格式化时区。
     */
    private final ZoneId zoneId;

    /**
     * 创建 Redis 分布式全局唯一标识生成器。
     *
     * @param stringRedisTemplate     Spring 字符串 Redis 模板
     * @param redisServerTimeProvider Redis 服务端时间提供器
     * @param properties              Redis 全局编号配置
     */
    public RedisGlobalIdGenerator(StringRedisTemplate stringRedisTemplate,
                                  RedisServerTimeProvider redisServerTimeProvider,
                                  RedisGlobalIdProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisServerTimeProvider = redisServerTimeProvider;
        this.properties = properties;
        this.zoneId = resolveZoneId(properties.getTimezone());
        validateProperties(properties);
    }

    /**
     * 生成全系统统一唯一标识。
     *
     * @return 22 位纯数字唯一标识
     */
    @Override
    public String nextId() {
        int maxAttempts = Math.max(1, properties.getMaxRetryTimes() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            SequenceResult sequenceResult = nextSequenceResult();
            if (!sequenceResult.overflow()) {
                return buildAndValidateId(sequenceResult.effectiveMillis(), sequenceResult.sequence());
            }
            if (attempt < maxAttempts) {
                sleepBeforeRetry();
            }
        }
        throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "全局唯一标识序列超过毫秒上限");
    }

    /**
     * 调用 Redis TIME 和 Lua 脚本获取有效毫秒与序列。
     *
     * @return Redis 序列结果
     */
    private SequenceResult nextSequenceResult() {
        long currentMillis = redisServerTimeProvider.currentTimeMillis();
        try {
            List<?> result = stringRedisTemplate.execute(
                    SEQUENCE_SCRIPT,
                    List.of(properties.getLastMillisKey()),
                    properties.getSeqKeyPrefix(),
                    String.valueOf(currentMillis),
                    String.valueOf(properties.getSeqKeyExpireSeconds()),
                    String.valueOf(properties.getMaxSequence())
            );
            return parseSequenceResult(result);
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis 生成全局唯一标识失败", exception);
        }
    }

    /**
     * 解析 Lua 脚本返回值。
     *
     * @param result Lua 脚本返回值
     * @return Redis 序列结果
     */
    private SequenceResult parseSequenceResult(List<?> result) {
        if (result == null || result.size() < 3) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis 生成全局唯一标识失败");
        }
        long effectiveMillis = toLong(result.get(0), "effectiveMillis");
        long sequence = toLong(result.get(1), "sequence");
        boolean overflow = toLong(result.get(2), "overflowFlag") == 1L;
        return new SequenceResult(effectiveMillis, sequence, overflow);
    }

    /**
     * 将 Lua 返回的数值对象转换为 long。
     *
     * @param value 数值对象
     * @param label 字段名称
     * @return long 值
     */
    private long toLong(Object value, String label) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis 生成全局唯一标识失败", exception);
            }
        }
        throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis 生成全局唯一标识失败:" + label);
    }

    /**
     * 构建并校验最终编号。
     *
     * @param effectiveMillis Redis 有效毫秒
     * @param sequence        毫秒内序列
     * @return 22 位纯数字编号
     */
    private String buildAndValidateId(long effectiveMillis, long sequence) {
        String body = formatTime(effectiveMillis)
                + String.format(Locale.ROOT, "%0" + properties.getSequenceLength() + "d", sequence);
        String id = body + LuhnMod10Utils.calculateCheckDigit(body);
        if (!GlobalIdValidator.isValid(id)) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "生成的全局唯一标识格式非法");
        }
        return id;
    }

    /**
     * 格式化 Redis 有效毫秒时间。
     *
     * @param effectiveMillis Redis 有效毫秒
     * @return 15 位时间片
     */
    private String formatTime(long effectiveMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(effectiveMillis), zoneId)
                .format(GlobalIdConstants.TIME_FORMATTER);
    }

    /**
     * 序列溢出后短暂等待再重试。
     */
    private void sleepBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(properties.getRetrySleepMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "全局唯一标识生成失败", exception);
        }
    }

    /**
     * 解析配置时区。
     *
     * @param timezone 时区配置
     * @return ZoneId
     */
    private ZoneId resolveZoneId(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "全局唯一标识时区配置非法", exception);
        }
    }

    /**
     * 校验 Redis 编号生成配置。
     *
     * @param target 待校验配置
     */
    private void validateProperties(RedisGlobalIdProperties target) {
        if (target.getSequenceLength() != GlobalIdConstants.SEQUENCE_LENGTH) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "全局唯一标识序列长度配置非法");
        }
        if (target.getMaxSequence() <= 0L || target.getMaxSequence() > GlobalIdConstants.DEFAULT_MAX_SEQUENCE) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "全局唯一标识最大序列配置非法");
        }
        if (target.getSeqKeyExpireSeconds() <= 0L || target.getMaxRetryTimes() < 0 || target.getRetrySleepMillis() < 0L) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "全局唯一标识 Redis 配置非法");
        }
    }

    /**
     * Redis Lua 脚本返回的序列结果。
     *
     * @param effectiveMillis 有效毫秒
     * @param sequence        毫秒内序列
     * @param overflow        是否超过毫秒内序列上限
     */
    private record SequenceResult(long effectiveMillis, long sequence, boolean overflow) {
    }
}
