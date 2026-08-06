package com.scott.payment.component.redis.generation;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheGenerationStore
 * @date : 2026-07-30 11:12
 * @email : scott_x@163.com
 * @description : Redis 缓存代际基础存储，原子协调当前代际与发布门禁，不承载具体业务规则
 * @status : create
 */
@Service
public class RedisCacheGenerationStore {

    /**
     * 代际读取 Lua 返回的已激活状态前缀。
     */
    private static final String ACTIVE_PREFIX = "ACTIVE:";

    /**
     * 发布门禁存在时返回的不可读状态，防止新旧代际混用。
     */
    private static final String PENDING = "PENDING";

    /**
     * Redis 字符串操作入口，用于执行同槽代际 Lua。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 环境隔离和 Cluster 同槽 Key 构造配置。
     */
    private final PaymentRedisProperties redisProperties;

    /**
     * Redis 业务指标记录器，不记录 namespace、generation 或发布 token。
     */
    private final RedisBusinessMetrics metrics;

    /**
     * 创建缓存代际存储。
     *
     * @param redisTemplate   Redis 字符串操作入口
     * @param redisProperties Redis Key 规范配置
     * @param metrics         Redis 业务指标记录器
     */
    @Autowired
    public RedisCacheGenerationStore(StringRedisTemplate redisTemplate,
                                     PaymentRedisProperties redisProperties,
                                     RedisBusinessMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.metrics = metrics;
    }

    /**
     * 创建不产生指标副作用的缓存代际存储，供纯单元测试直接构造。
     *
     * @param redisTemplate   Redis 字符串操作入口
     * @param redisProperties Redis Key 规范配置
     */
    public RedisCacheGenerationStore(StringRedisTemplate redisTemplate,
                                     PaymentRedisProperties redisProperties) {
        this(redisTemplate, redisProperties, RedisBusinessMetrics.noop());
    }

    /**
     * 原子读取当前代际；首次读取时生成全新代际，避免复用遗留缓存。
     *
     * @param namespace 受控缓存命名空间
     * @return 当前代际或发布中的不可读状态
     */
    public RedisCacheGenerationState current(String namespace) {
        long startNanos = System.nanoTime();
        try {
            RedisCacheGenerationState state = readGenerationState(
                    namespace,
                    "g-" + UUID.randomUUID()
            );
            if (!state.cacheReadable()) {
                record(
                        RedisBusinessMetrics.Operation.READ,
                        RedisBusinessMetrics.Outcome.PENDING,
                        startNanos
                );
                return RedisCacheGenerationState.pending();
            }
            record(
                    RedisBusinessMetrics.Operation.READ,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    startNanos
            );
            return state;
        } catch (RuntimeException exception) {
            record(
                    RedisBusinessMetrics.Operation.READ,
                    RedisBusinessMetrics.Outcome.ERROR,
                    startNanos
            );
            if (StringUtils.hasText(exception.getMessage())
                    && !exception.getMessage().contains("invalid result")) {
                metrics.recordLuaFailure(
                        RedisBusinessMetrics.Script.CACHE_GENERATION_READ,
                        metrics.classifyFailure(exception)
                );
            }
            throw exception;
        }
    }

    /**
     * 获取单个命名空间的发布门禁并生成待切换代际。
     *
     * @param namespace 受控缓存命名空间
     * @param gateTtl 发布门禁最长持有时间
     * @return 本次发布凭证
     */
    public RedisCachePublication begin(String namespace, Duration gateTtl) {
        if (gateTtl == null || gateTtl.isZero() || gateTtl.isNegative()) {
            throw new IllegalArgumentException("Redis cache publication gate TTL must be positive");
        }
        String token = "t-" + UUID.randomUUID();
        String generation = "g-" + UUID.randomUUID();
        long startNanos = System.nanoTime();
        try {
            Long acquired = redisTemplate.execute(
                    PaymentRedisScripts.cacheGenerationBeginV1(),
                    List.of(publicationKey(namespace)),
                    token,
                    String.valueOf(gateTtl.toMillis())
            );
            if (!Long.valueOf(1L).equals(acquired)) {
                throw new IllegalStateException(
                        "Redis cache generation publication is already in progress");
            }
            record(
                    RedisBusinessMetrics.Operation.ACQUIRE,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    startNanos
            );
            return new RedisCachePublication(namespace, token, generation);
        } catch (RuntimeException exception) {
            if (StringUtils.hasText(exception.getMessage())
                    && exception.getMessage().contains("already in progress")) {
                record(
                        RedisBusinessMetrics.Operation.ACQUIRE,
                        RedisBusinessMetrics.Outcome.CONTENDED,
                        startNanos
                );
            } else {
                record(
                        RedisBusinessMetrics.Operation.ACQUIRE,
                        RedisBusinessMetrics.Outcome.ERROR,
                        startNanos
                );
                metrics.recordLuaFailure(
                        RedisBusinessMetrics.Script.CACHE_GENERATION_BEGIN,
                        metrics.classifyFailure(exception)
                );
            }
            throw exception;
        }
    }

    /**
     * 原子切换当前代际并释放发布门禁；相同代际重复提交视为成功。
     *
     * @param publication 发布凭证
     * @return 是否完成切换或命中幂等结果
     */
    public boolean commit(RedisCachePublication publication) {
        if (publication == null) {
            return false;
        }
        long startNanos = System.nanoTime();
        try {
            Long committed = redisTemplate.execute(
                    PaymentRedisScripts.cacheGenerationCommitV1(),
                    List.of(
                            generationKey(publication.namespace()),
                            publicationKey(publication.namespace())
                    ),
                    publication.token(),
                    publication.generation()
            );
            if (!Long.valueOf(1L).equals(committed)) {
                record(
                        RedisBusinessMetrics.Operation.WRITE,
                        RedisBusinessMetrics.Outcome.CONTENDED,
                        startNanos
                );
                return false;
            }
            record(
                    RedisBusinessMetrics.Operation.WRITE,
                    RedisBusinessMetrics.Outcome.SUCCESS,
                    startNanos
            );
            return true;
        } catch (RuntimeException exception) {
            record(
                    RedisBusinessMetrics.Operation.WRITE,
                    RedisBusinessMetrics.Outcome.ERROR,
                    startNanos
            );
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.CACHE_GENERATION_COMMIT,
                    metrics.classifyFailure(exception)
            );
            throw exception;
        }
    }

    /**
     * 在数据库事务未提交时安全释放发布门禁，不切换当前代际。
     *
     * @param publication 发布凭证
     * @return 是否由当前持有者释放门禁
     */
    public boolean abort(RedisCachePublication publication) {
        if (publication == null) {
            return false;
        }
        long startNanos = System.nanoTime();
        try {
            boolean success = releasePublicationGate(
                    publication.namespace(),
                    publication.token()
            );
            record(
                    RedisBusinessMetrics.Operation.RELEASE,
                    success
                            ? RedisBusinessMetrics.Outcome.SUCCESS
                            : RedisBusinessMetrics.Outcome.CONTENDED,
                    startNanos
            );
            return success;
        } catch (RuntimeException exception) {
            record(
                    RedisBusinessMetrics.Operation.RELEASE,
                    RedisBusinessMetrics.Outcome.ERROR,
                    startNanos
            );
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.TOKEN_LEASE_RELEASE,
                    metrics.classifyFailure(exception)
            );
            throw exception;
        }
    }

    /**
     * 记录缓存代际操作，不把 namespace、generation 或发布 token 作为指标维度。
     *
     * @param operation  操作类型
     * @param outcome    操作结果
     * @param startNanos 本次 Redis 操作起始时间
     */
    private void record(RedisBusinessMetrics.Operation operation,
                        RedisBusinessMetrics.Outcome outcome,
                        long startNanos) {
        metrics.recordOperation(
                RedisBusinessMetrics.Feature.CACHE_GENERATION,
                operation,
                outcome,
                System.nanoTime() - startNanos
        );
    }

    /**
     * 读取当前代际；不存在时使用调用方给出的 generation 原子初始化。
     *
     * @param namespace 已登记的缓存命名空间
     * @param initialGeneration Key 不存在时写入的初始 generation
     * @return 可读代际或发布中的不可读状态
     */
    private RedisCacheGenerationState readGenerationState(String namespace,
                                                          String initialGeneration) {
        String result = redisTemplate.execute(
                PaymentRedisScripts.cacheGenerationReadV1(),
                List.of(generationKey(namespace), publicationKey(namespace)),
                initialGeneration
        );
        if (PENDING.equals(result)) {
            return RedisCacheGenerationState.pending();
        }
        if (!StringUtils.hasText(result) || !result.startsWith(ACTIVE_PREFIX)
                || result.length() == ACTIVE_PREFIX.length()) {
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.CACHE_GENERATION_READ,
                    RedisBusinessMetrics.Failure.INVALID_RESULT
            );
            throw new IllegalStateException("Redis cache generation read returned an invalid result");
        }
        return RedisCacheGenerationState.active(result.substring(ACTIVE_PREFIX.length()));
    }

    /**
     * 仅允许当前 token 持有者释放发布门禁。
     *
     * @param namespace 缓存命名空间
     * @param token 发布持有者 token
     * @return 当前持有者成功释放时为 true
     */
    private boolean releasePublicationGate(String namespace, String token) {
        Long released = redisTemplate.execute(
                PaymentRedisScripts.tokenLeaseReleaseV1(),
                List.of(publicationKey(namespace)),
                token
        );
        return Long.valueOf(1L).equals(released);
    }

    /**
     * 构造缓存当前 generation Key，与发布门禁使用相同 Hash Tag。
     *
     * @param namespace 已登记的缓存命名空间
     * @return 当前 generation 物理 Key
     */
    private String generationKey(String namespace) {
        return redisProperties.coLocatedBusinessKey(
                "cache", "generation", namespace, "current");
    }

    /**
     * 构造 generation 发布门禁 Key，与当前代际使用相同 Hash Tag。
     *
     * @param namespace 已登记的缓存命名空间
     * @return 发布门禁物理 Key
     */
    private String publicationKey(String namespace) {
        return redisProperties.coLocatedBusinessKey(
                "cache", "generation", namespace, "publication");
    }
}
