package com.scott.payment.component.redis.idempotent.impl;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisIdempotentServiceImpl
 * @date : 2026-05-31 20:47
 * @email : scott_x@163.com
 * @description : 实现 Redis 辅助幂等；MQ 路径使用有界时间桶 ZSet，异常时显式降级到数据库唯一约束
 * @status : create
 */
@Slf4j
@Service
public class RedisIdempotentServiceImpl implements IdempotentService {

    /**
     * MQ 去重 Lua 返回值：本次消息成功取得辅助处理权。
     */
    private static final long MQ_ACQUIRED = 1L;

    /**
     * MQ 去重 Lua 返回值：摘要已存在，当前消息属于重复投递。
     */
    private static final long MQ_DUPLICATE = 0L;

    /**
     * MQ 去重 Lua 返回值：时间桶已达到成员容量上限，调用方必须走持久化幂等兜底。
     */
    private static final long MQ_CAPACITY_EXCEEDED = -1L;

    /**
     * 单个 MQ 去重时间桶的硬上限，防止错误配置形成 Redis 大 Key。
     */
    private static final int ABSOLUTE_MAX_MEMBERS_PER_BUCKET = 1_000_000;

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 支付系统 Redis Key 配置。
     */
    private final PaymentRedisProperties redisProperties;

    /**
     * Redis 业务指标记录器，仅写入封闭的幂等结果和失败分类。
     */
    private final RedisBusinessMetrics metrics;

    /**
     * 基础设施降级告警是否已经输出；消费者仍会为每条 FALLBACK 消息记录可追踪事件。
     */
    private volatile boolean fallbackWarningLogged;

    /**
     * 创建 Redis 幂等控制服务。
     *
     * @param stringRedisTemplateProvider Spring 字符串 Redis 模板提供器
     * @param redisProperties             Redis Key 和容量边界配置
     * @param metrics                     Redis 业务指标记录器
     */
    @Autowired
    public RedisIdempotentServiceImpl(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                      PaymentRedisProperties redisProperties,
                                      RedisBusinessMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.redisProperties = redisProperties;
        this.metrics = metrics;
    }

    /**
     * 创建不产生指标副作用的 Redis 幂等服务，供纯单元测试和隔离集成测试直接构造。
     *
     * @param stringRedisTemplateProvider Spring 字符串 Redis 模板提供器
     * @param redisProperties             Redis Key 和容量边界配置
     */
    public RedisIdempotentServiceImpl(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                      PaymentRedisProperties redisProperties) {
        this(stringRedisTemplateProvider, redisProperties, RedisBusinessMetrics.noop());
    }

    /**
     * 获取幂等处理权。
     *
     * @param idempotentKey 幂等业务键
     * @param ttlSeconds    幂等有效期，单位秒
     * @return 是否获取成功
     */
    @Override
    public boolean acquire(String idempotentKey, long ttlSeconds) {
        if (!StringUtils.hasText(idempotentKey)) {
            throw new IllegalArgumentException("idempotent key can not be blank");
        }
        if (ttlSeconds <= 0) {
            return false;
        }
        if (stringRedisTemplate == null) {
            metrics.recordFallback(
                    RedisBusinessMetrics.Feature.IDEMPOTENCY,
                    RedisBusinessMetrics.FallbackReason.CLIENT_MISSING
            );
            logFallbackWarningIfNecessary("redisTemplateMissing", "single-key", null);
            return true;
        }
        String physicalKey = redisProperties.key("idempotency", "key", RedisKeyDigest.sha256(idempotentKey));
        long startNanos = System.nanoTime();
        try {
            boolean acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(
                            physicalKey,
                            String.valueOf(Instant.now().toEpochMilli()),
                            Duration.ofSeconds(ttlSeconds)
                    ));
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.IDEMPOTENCY,
                    RedisBusinessMetrics.Operation.ACQUIRE,
                    acquired
                            ? RedisBusinessMetrics.Outcome.SUCCESS
                            : RedisBusinessMetrics.Outcome.DUPLICATE,
                    System.nanoTime() - startNanos
            );
            return acquired;
        } catch (RuntimeException exception) {
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.IDEMPOTENCY,
                    RedisBusinessMetrics.Operation.ACQUIRE,
                    RedisBusinessMetrics.Outcome.ERROR,
                    System.nanoTime() - startNanos
            );
            throw exception;
        }
    }

    /**
     * 兼容既有布尔调用方获取 MQ 辅助幂等结果。
     *
     * <p>FALLBACK 返回 true，使既有调用方继续执行并抵达数据库唯一约束；新 MQ 消费者应调用
     * {@link #acquireMq(String, String, long)} 区分降级并记录可观测事件。</p>
     *
     * @param namespace   MQ 消费业务命名空间
     * @param businessKey 消息业务幂等键
     * @param ttlSeconds  去重有效期，单位秒
     * @return ACQUIRED 或 FALLBACK 时为 true，命中重复时为 false
     */
    @Override
    public boolean acquire(String namespace, String businessKey, long ttlSeconds) {
        return acquireMq(namespace, businessKey, ttlSeconds) != IdempotentAcquireResult.DUPLICATE;
    }

    /**
     * 使用当前桶和前一桶两个同槽 ZSet 获取 MQ 辅助幂等处理权。
     *
     * <p>桶宽等于业务 TTL，脚本使用 Redis TIME 清理过期成员并跨桶查重。Redis 缺失、执行异常
     * 或当前桶达到容量上限时返回 FALLBACK，调用方必须继续到数据库唯一约束。</p>
     *
     * @param namespace   低基数消费业务命名空间
     * @param businessKey 消息业务幂等键，写入前转换为 SHA-256 摘要
     * @param ttlSeconds  去重有效期，单位秒
     * @return 获取成功、命中重复或降级数据库兜底
     */
    @Override
    public IdempotentAcquireResult acquireMq(String namespace, String businessKey, long ttlSeconds) {
        requireNamespaceAndBusinessKey(namespace, businessKey);
        long ttlMillis = validatedMqTtlMillis(ttlSeconds);
        int maxMembers = validatedMaxMembersPerBucket();
        if (stringRedisTemplate == null) {
            metrics.recordFallback(
                    RedisBusinessMetrics.Feature.MQ_DEDUP,
                    RedisBusinessMetrics.FallbackReason.CLIENT_MISSING
            );
            logFallbackWarningIfNecessary("redisTemplateMissing", namespace, null);
            return IdempotentAcquireResult.FALLBACK;
        }
        long startNanos = System.nanoTime();
        try {
            long redisNowMillis = redisCurrentTimeMillis();
            List<String> bucketKeys = mqBucketKeys(namespace, redisNowMillis, ttlMillis);
            Long result = stringRedisTemplate.execute(
                    PaymentRedisScripts.mqDedupAcquireV1(),
                    bucketKeys,
                    RedisKeyDigest.sha256(businessKey),
                    String.valueOf(ttlMillis),
                    String.valueOf(maxMembers)
            );
            if (Long.valueOf(MQ_ACQUIRED).equals(result)) {
                recordMqOutcome(RedisBusinessMetrics.Outcome.SUCCESS, startNanos);
                return IdempotentAcquireResult.ACQUIRED;
            }
            if (Long.valueOf(MQ_DUPLICATE).equals(result)) {
                recordMqOutcome(RedisBusinessMetrics.Outcome.DUPLICATE, startNanos);
                return IdempotentAcquireResult.DUPLICATE;
            }
            if (Long.valueOf(MQ_CAPACITY_EXCEEDED).equals(result)) {
                recordMqFallback(
                        RedisBusinessMetrics.FallbackReason.CAPACITY_EXCEEDED,
                        RedisBusinessMetrics.Failure.CAPACITY,
                        startNanos
                );
                logFallbackWarningIfNecessary("bucketCapacityExceeded", namespace, null);
                return IdempotentAcquireResult.FALLBACK;
            }
            recordMqFallback(
                    RedisBusinessMetrics.FallbackReason.UNEXPECTED_RESULT,
                    RedisBusinessMetrics.Failure.INVALID_RESULT,
                    startNanos
            );
            logFallbackWarningIfNecessary("unexpectedScriptResult", namespace, null);
            return IdempotentAcquireResult.FALLBACK;
        } catch (RuntimeException exception) {
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.MQ_DEDUP,
                    RedisBusinessMetrics.Operation.ACQUIRE,
                    RedisBusinessMetrics.Outcome.FALLBACK,
                    System.nanoTime() - startNanos
            );
            metrics.recordFallback(
                    RedisBusinessMetrics.Feature.MQ_DEDUP,
                    metrics.classifyFallback(exception)
            );
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.MQ_DEDUP_ACQUIRE,
                    metrics.classifyFailure(exception)
            );
            logFallbackWarningIfNecessary("redisExecutionFailed", namespace, exception);
            return IdempotentAcquireResult.FALLBACK;
        }
    }

    /**
     * 释放失败业务占用的幂等键，使消息或请求可以重新处理。
     *
     * @param idempotentKey 幂等业务键
     */
    @Override
    public void release(String idempotentKey) {
        if (!StringUtils.hasText(idempotentKey)) {
            return;
        }
        if (stringRedisTemplate == null) {
            logFallbackWarningIfNecessary("redisTemplateMissing", "single-key", null);
            return;
        }
        stringRedisTemplate.delete(redisProperties.key("idempotency", "key", RedisKeyDigest.sha256(idempotentKey)));
    }

    /**
     * 从低基数 ZSET 中移除失败消费的业务摘要。
     *
     * @param namespace   幂等业务命名空间
     * @param businessKey 业务幂等键
     */
    @Override
    public void release(String namespace, String businessKey) {
        releaseMq(namespace, businessKey, redisProperties.getMqDedup().getMaxTtlSeconds());
    }

    /**
     * 从当前时间桶和前一时间桶移除失败消费的业务摘要。
     *
     * @param namespace   MQ 消费业务命名空间
     * @param businessKey 消息业务幂等键
     * @param ttlSeconds  获取处理权时使用的去重有效期，单位秒
     */
    @Override
    public void releaseMq(String namespace, String businessKey, long ttlSeconds) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(businessKey)) {
            return;
        }
        if (stringRedisTemplate == null) {
            logFallbackWarningIfNecessary("redisTemplateMissing", namespace, null);
            return;
        }
        long ttlMillis = validatedMqTtlMillis(ttlSeconds);
        long redisNowMillis = redisCurrentTimeMillis();
        String businessKeyDigest = RedisKeyDigest.sha256(businessKey);
        for (String bucketKey : mqBucketKeys(namespace, redisNowMillis, ttlMillis)) {
            stringRedisTemplate.opsForZSet().remove(bucketKey, businessKeyDigest);
        }
    }

    /**
     * 校验 MQ 辅助幂等的命名空间和业务键。
     * <p>
     * 业务键随后只以 SHA-256 摘要进入 ZSet，不以明文写入 Redis 或日志；最终幂等仍由数据库
     * 唯一约束或消费状态记录兜底。
     * </p>
     *
     * @param namespace   MQ 消费业务命名空间
     * @param businessKey 消息业务幂等键
     * @throws IllegalArgumentException 任一输入为空时抛出
     */
    private void requireNamespaceAndBusinessKey(String namespace, String businessKey) {
        if (!StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException("idempotent namespace can not be blank");
        }
        if (!StringUtils.hasText(businessKey)) {
            throw new IllegalArgumentException("idempotent business key can not be blank");
        }
    }

    /**
     * 校验 MQ 去重 TTL，并转换为脚本使用的毫秒值。
     *
     * @param ttlSeconds 业务去重有效期，单位秒
     * @return 去重有效期，单位毫秒
     */
    private long validatedMqTtlMillis(long ttlSeconds) {
        long maxTtlSeconds = redisProperties.getMqDedup().getMaxTtlSeconds();
        if (ttlSeconds <= 0L || maxTtlSeconds <= 0L || ttlSeconds > maxTtlSeconds) {
            throw new IllegalArgumentException("MQ idempotent TTL is outside the configured boundary");
        }
        return Math.multiplyExact(ttlSeconds, 1_000L);
    }

    /**
     * 校验单桶成员上限，防止错误配置绕过大 Key 保护。
     *
     * @return 单桶最大摘要数量，单位为个
     */
    private int validatedMaxMembersPerBucket() {
        int maxMembers = redisProperties.getMqDedup().getMaxMembersPerBucket();
        if (maxMembers <= 0 || maxMembers > ABSOLUTE_MAX_MEMBERS_PER_BUCKET) {
            throw new IllegalArgumentException("MQ idempotent bucket capacity is outside the absolute boundary");
        }
        return maxMembers;
    }

    /**
     * 从 Redis 服务端读取分桶时间，避免多实例本地时钟偏差把同一消息写入不相邻的桶。
     *
     * @return Redis 服务端 epochMillis
     */
    private long redisCurrentTimeMillis() {
        Long currentMillis = stringRedisTemplate.execute(
                (RedisCallback<Long>) connection -> connection.serverCommands().time(TimeUnit.MILLISECONDS)
        );
        if (currentMillis == null || currentMillis <= 0L) {
            throw new IllegalStateException("Redis TIME returned an invalid value");
        }
        return currentMillis;
    }

    /**
     * 构造当前桶和前一桶的 Cluster 同槽物理 Key。
     *
     * <p>命名空间保留为可识别业务片段，Hash Tag 只保存槽身份摘要，不包含消息或商户敏感值。</p>
     *
     * @param namespace     MQ 消费业务命名空间
     * @param nowMillis    Redis 服务端当前时间，单位毫秒
     * @param bucketMillis 桶宽，等于去重 TTL，单位毫秒
     * @return 依次为当前桶和前一桶的两个同槽 Key
     */
    private List<String> mqBucketKeys(String namespace, long nowMillis, long bucketMillis) {
        long currentBucket = Math.floorDiv(nowMillis, bucketMillis);
        return List.of(
                redisProperties.coLocatedBusinessKey(
                        "mq", "dedup", namespace, namespace, String.valueOf(currentBucket)),
                redisProperties.coLocatedBusinessKey(
                        "mq", "dedup", namespace, namespace, String.valueOf(currentBucket - 1L))
        );
    }

    /**
     * 记录 MQ 辅助去重的成功或重复结果。
     *
     * @param outcome    获取成功或命中重复
     * @param startNanos 本次 Redis 操作起始时间
     */
    private void recordMqOutcome(RedisBusinessMetrics.Outcome outcome, long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.MQ_DEDUP,
                RedisBusinessMetrics.Operation.ACQUIRE,
                outcome,
                System.nanoTime() - startNanos
        );
    }

    /**
     * 记录 MQ 辅助去重的脚本语义降级。
     *
     * @param reason     降级原因
     * @param failure    Lua 失败分类
     * @param startNanos 本次 Redis 操作起始时间
     */
    private void recordMqFallback(RedisBusinessMetrics.FallbackReason reason,
                                  RedisBusinessMetrics.Failure failure,
                                  long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.MQ_DEDUP,
                RedisBusinessMetrics.Operation.ACQUIRE,
                RedisBusinessMetrics.Outcome.FALLBACK,
                System.nanoTime() - startNanos
        );
        metrics.recordFallback(RedisBusinessMetrics.Feature.MQ_DEDUP, reason);
        metrics.recordLuaFailure(RedisBusinessMetrics.Script.MQ_DEDUP_ACQUIRE, failure);
    }

    /**
     * Redis 降级时输出一次基础设施告警，避免持续故障造成日志风暴。
     *
     * @param reason    降级原因编码
     * @param namespace MQ 消费业务命名空间
     * @param exception Redis 调用异常；无异常对象时允许为空
     */
    private void logFallbackWarningIfNecessary(String reason, String namespace, RuntimeException exception) {
        if (fallbackWarningLogged) {
            return;
        }
        synchronized (this) {
            if (fallbackWarningLogged) {
                return;
            }
            if (exception == null) {
                log.warn("event: REDIS_MQ_IDEMPOTENT_FALLBACK stage=MQ_DEDUP namespace: {} reason: {} "
                                + "action: continueToDatabaseUniqueConstraint",
                        namespace, reason);
            } else {
                log.warn("event: REDIS_MQ_IDEMPOTENT_FALLBACK stage=MQ_DEDUP namespace: {} reason: {} "
                                + "action: continueToDatabaseUniqueConstraint",
                        namespace, reason, exception);
            }
            fallbackWarningLogged = true;
        }
    }
}
