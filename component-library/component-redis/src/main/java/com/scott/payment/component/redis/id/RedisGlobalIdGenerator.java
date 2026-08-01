package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdConstants;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.id.GlobalIdValidator;
import com.scott.payment.component.core.id.LuhnMod10Utils;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdGenerator
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : 基于 Redis TIME 与单 Hash Lua 生成 22 位全局 ID，Redis 故障或状态配置异常时禁止本地降级
 * @status : create
 */
public class RedisGlobalIdGenerator implements GlobalIdGenerator {

    private static final Pattern STATE_KEY_PATTERN =
            Pattern.compile("^acquiring:[A-Za-z0-9][A-Za-z0-9._-]{0,127}:global-id:state$");

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
     * Redis 业务指标记录器，不包含生成的编号或状态 Key 标签。
     */
    private final RedisBusinessMetrics metrics;

    /**
     * 创建 Redis 分布式全局唯一标识生成器。
     *
     * @param stringRedisTemplate     Spring 字符串 Redis 模板
     * @param redisServerTimeProvider Redis 服务端时间提供器
     * @param properties              Redis 全局编号配置
     * @param metrics                 Redis 业务指标记录器
     */
    public RedisGlobalIdGenerator(StringRedisTemplate stringRedisTemplate,
                                  RedisServerTimeProvider redisServerTimeProvider,
                                  RedisGlobalIdProperties properties,
                                  RedisBusinessMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisServerTimeProvider = redisServerTimeProvider;
        this.properties = properties;
        this.metrics = metrics;
        this.zoneId = resolveZoneId(properties.getTimezone());
        validateProperties(properties);
    }

    /**
     * 创建不产生指标副作用的 Redis 全局编号生成器，供纯单元测试和隔离测试直接构造。
     *
     * @param stringRedisTemplate     Spring 字符串 Redis 模板
     * @param redisServerTimeProvider Redis 服务端时间提供器
     * @param properties              Redis 全局编号配置
     */
    public RedisGlobalIdGenerator(StringRedisTemplate stringRedisTemplate,
                                  RedisServerTimeProvider redisServerTimeProvider,
                                  RedisGlobalIdProperties properties) {
        this(
                stringRedisTemplate,
                redisServerTimeProvider,
                properties,
                RedisBusinessMetrics.noop()
        );
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
        long startNanos = System.nanoTime();
        boolean scriptInvoked = false;
        try {
            long currentMillis = redisServerTimeProvider.currentTimeMillis();
            scriptInvoked = true;
            List<?> result = stringRedisTemplate.execute(
                    PaymentRedisScripts.globalIdSequenceV1(),
                    List.of(properties.getStateKey()),
                    String.valueOf(currentMillis),
                    String.valueOf(properties.getMaxSequence()),
                    String.valueOf(properties.getRestoreFloorEpochMillis())
            );
            SequenceResult sequenceResult = parseSequenceResult(result);
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.GLOBAL_ID,
                    RedisBusinessMetrics.Operation.EXECUTE,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    System.nanoTime() - startNanos
            );
            return sequenceResult;
        } catch (ServiceException exception) {
            recordSequenceFailure(exception, scriptInvoked, startNanos);
            throw exception;
        } catch (RuntimeException exception) {
            recordSequenceFailure(exception, scriptInvoked, startNanos);
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis 生成全局唯一标识失败", exception);
        }
    }

    /**
     * 记录全局编号 Redis TIME 或 Lua 调用失败；失败详情只进入日志异常链，不进入指标标签。
     *
     * @param exception     Redis 调用异常
     * @param scriptInvoked 是否已经进入 Lua 执行阶段
     * @param startNanos    本次编号序列请求起始时间
     */
    private void recordSequenceFailure(RuntimeException exception,
                                       boolean scriptInvoked,
                                       long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.GLOBAL_ID,
                RedisBusinessMetrics.Operation.EXECUTE,
                RedisBusinessMetrics.Outcome.ERROR,
                System.nanoTime() - startNanos
        );
        if (scriptInvoked) {
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.GLOBAL_ID_SEQUENCE,
                    metrics.classifyFailure(exception)
            );
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
        if (!StringUtils.hasText(target.getStateKey())
                || !STATE_KEY_PATTERN.matcher(target.getStateKey()).matches()
                || target.getMaxRetryTimes() < 0
                || target.getRetrySleepMillis() < 0L) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "全局唯一标识 Redis 配置非法");
        }
        boolean hasRestoreFloor = target.getRestoreFloorEpochMillis() > 0L;
        if (target.getRestoreFloorEpochMillis() < 0L
                || target.isRestoreAcknowledged() != hasRestoreFloor) {
            throw new ServiceException(
                    ApiResultEnum.PARAM_INVALID.getCode(),
                    "全局唯一标识状态恢复配置非法"
            );
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
