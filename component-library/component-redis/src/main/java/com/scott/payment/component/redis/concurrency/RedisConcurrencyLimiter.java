package com.scott.payment.component.redis.concurrency;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisConcurrencyLimiter
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : Redis 基础设施层的集群级有界并发租约，使用摘要身份和固定租期限制同一主体的并发任务数。
 * @status : create
 */
@Service
@Slf4j
public class RedisConcurrencyLimiter {

    /** Lua 获取结果：已占用一个并发租约。 */
    private static final long ACQUIRED = 1L;

    /** Redis 脚本执行和租约 ZSet 释放入口。 */
    private final StringRedisTemplate redisTemplate;
    /** 环境隔离、Cluster Hash Tag 与身份摘要 Key 规则。 */
    private final PaymentRedisProperties redisProperties;

    /**
     * 创建使用 Redis 服务端时钟的集群级并发限制器。
     *
     * @param redisTemplate Redis 字符串与脚本执行入口
     * @param redisProperties 环境隔离 Key 配置
     */
    public RedisConcurrencyLimiter(StringRedisTemplate redisTemplate,
                                   PaymentRedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

    /**
     * 在集群级并发预算内执行任务；竞争失败不执行回调，Redis 异常直接向上抛出以保持 Fail Closed。
     *
     * @param domain Redis 业务域安全片段
     * @param business Redis 业务用途安全片段
     * @param identity 当前用户、商户或任务主体的稳定身份，物理 Key 只保存其摘要
     * @param maxConcurrent 同一身份允许同时持有的最大租约数
     * @param leaseTime 异常退出后的最长占位时间
     * @param action 取得租约后执行的同步任务
     * @return true 表示取得租约并完成回调，false 表示并发预算已满
     */
    public boolean execute(String domain,
                           String business,
                           String identity,
                           int maxConcurrent,
                           Duration leaseTime,
                           Runnable action) {
        if (!StringUtils.hasText(identity)) {
            throw new IllegalArgumentException("Concurrency identity must not be blank");
        }
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("maxConcurrent must be positive");
        }
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            throw new IllegalArgumentException("leaseTime must be positive");
        }
        if (action == null) {
            throw new IllegalArgumentException("Concurrency action must not be null");
        }

        String key = redisProperties.coLocatedBusinessKey(domain, business, identity, "leases");
        String token = "t-" + UUID.randomUUID();
        long leaseMillis;
        try {
            leaseMillis = leaseTime.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("leaseTime is too large", exception);
        }
        if (leaseMillis <= 0L) {
            throw new IllegalArgumentException("leaseTime must be positive");
        }
        Long acquired = redisTemplate.execute(
                PaymentRedisScripts.concurrencyLeaseAcquireV1(),
                List.of(key),
                Long.toString(leaseMillis),
                Integer.toString(maxConcurrent),
                token
        );
        if (!Long.valueOf(ACQUIRED).equals(acquired)) {
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            try {
                redisTemplate.opsForZSet().remove(key, token);
            } catch (RuntimeException exception) {
                log.warn("event: REDIS_CONCURRENCY_LEASE_RELEASE_FAILED domain: {} business: {} exceptionType: {}",
                        domain, business, exception.getClass().getSimpleName());
            }
        }
    }
}
