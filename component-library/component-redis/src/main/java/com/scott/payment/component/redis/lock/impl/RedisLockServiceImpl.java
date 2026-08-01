package com.scott.payment.component.redis.lock.impl;

import com.scott.payment.component.redis.lock.RedisLockService;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisLockServiceImpl
 * @date : 2026-05-31 20:48
 * @email : scott_x@163.com
 * @description : Redis 分布式锁服务实现
 * @status : create
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisLockServiceImpl implements RedisLockService {

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Redis 业务指标记录器，只使用固定的锁操作和结果维度。
     */
    private final RedisBusinessMetrics metrics;

    /**
     * 创建具备低基数观测能力的 Redis 分布式锁服务。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     * @param metrics             Redis 业务指标记录器
     */
    @Autowired
    public RedisLockServiceImpl(StringRedisTemplate stringRedisTemplate,
                                RedisBusinessMetrics metrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.metrics = metrics;
    }

    /**
     * 创建不产生指标副作用的 Redis 分布式锁服务，供纯单元测试和隔离测试直接构造。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     */
    public RedisLockServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, RedisBusinessMetrics.noop());
    }

    /**
     * 尝试获取 Redis 分布式锁。
     *
     * @param key        锁键
     * @param value      锁值
     * @param ttlSeconds 锁过期时间，单位秒
     * @return 是否获取成功
     */
    @Override
    public boolean tryLock(String key, String value, long ttlSeconds) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value) || ttlSeconds <= 0) {
            return false;
        }
        long startNanos = System.nanoTime();
        try {
            boolean acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds)));
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.LOCK,
                    RedisBusinessMetrics.Operation.ACQUIRE,
                    acquired
                            ? RedisBusinessMetrics.Outcome.SUCCESS
                            : RedisBusinessMetrics.Outcome.CONTENDED,
                    System.nanoTime() - startNanos
            );
            return acquired;
        } catch (RuntimeException exception) {
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.LOCK,
                    RedisBusinessMetrics.Operation.ACQUIRE,
                    RedisBusinessMetrics.Outcome.ERROR,
                    System.nanoTime() - startNanos
            );
            throw exception;
        }
    }

    /**
     * 释放 Redis 分布式锁。
     *
     * @param key   锁键
     * @param value 锁值
     */
    @Override
    public void unlock(String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        long startNanos = System.nanoTime();
        try {
            Long released = stringRedisTemplate.execute(
                    PaymentRedisScripts.lockReleaseV1(),
                    List.of(key),
                    value
            );
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.LOCK,
                    RedisBusinessMetrics.Operation.RELEASE,
                    Long.valueOf(1L).equals(released)
                            ? RedisBusinessMetrics.Outcome.SUCCESS
                            : RedisBusinessMetrics.Outcome.CONTENDED,
                    System.nanoTime() - startNanos
            );
        } catch (RuntimeException exception) {
            metrics.recordOperation(
                    RedisBusinessMetrics.Feature.LOCK,
                    RedisBusinessMetrics.Operation.RELEASE,
                    RedisBusinessMetrics.Outcome.ERROR,
                    System.nanoTime() - startNanos
            );
            metrics.recordLuaFailure(
                    RedisBusinessMetrics.Script.LOCK_RELEASE,
                    metrics.classifyFailure(exception)
            );
            throw exception;
        }
    }
}
