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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisConcurrencyLimiter
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : Redis 基础设施层的集群级有界并发租约，使用摘要身份和自动续租限制同一主体的并发任务数。
 * @status : create
 */
@Service
@Slf4j
public class RedisConcurrencyLimiter {

    /** Lua 获取结果：已占用一个并发租约。 */
    private static final long ACQUIRED = 1L;

    /**
     * 进程级守护续租线程；Redis 调用受客户端超时约束，任务结束后会取消对应调度。
     */
    private static final ScheduledExecutorService LEASE_RENEWAL_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "redis-concurrency-lease-renewer");
                thread.setDaemon(true);
                return thread;
            });

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
     * @param leaseTime 续租周期基准，以及进程异常退出后的最长占位时间
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
        ScheduledFuture<?> renewalTask = scheduleRenewal(
                key, token, leaseMillis, domain, business);
        try {
            action.run();
            return true;
        } finally {
            renewalTask.cancel(false);
            try {
                redisTemplate.opsForZSet().remove(key, token);
            } catch (RuntimeException exception) {
                log.warn("event: REDIS_CONCURRENCY_LEASE_RELEASE_FAILED domain: {} business: {} exceptionType: {}",
                        domain, business, exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * 在租约三分之一周期后开始续租，确保无行数上限的同步任务不会在正常执行期间失去并发占位。
     *
     * @param key Redis 并发租约 ZSet Key
     * @param token 当前执行实例的随机租约标识
     * @param leaseMillis 单次租约有效期，单位毫秒
     * @param domain 日志使用的非敏感业务域
     * @param business 日志使用的非敏感业务用途
     * @return 可在任务结束时取消的续租调度
     */
    private ScheduledFuture<?> scheduleRenewal(String key,
                                               String token,
                                               long leaseMillis,
                                               String domain,
                                               String business) {
        long renewalIntervalMillis = Math.max(1L, leaseMillis / 3L);
        return LEASE_RENEWAL_EXECUTOR.scheduleWithFixedDelay(
                () -> renewLease(key, token, leaseMillis, domain, business),
                renewalIntervalMillis,
                renewalIntervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    /** 仅在当前 token 仍存在时原子续租；短暂 Redis 故障由后续周期继续重试。 */
    private void renewLease(String key,
                            String token,
                            long leaseMillis,
                            String domain,
                            String business) {
        try {
            Long renewed = redisTemplate.execute(
                    PaymentRedisScripts.concurrencyLeaseRenewV1(),
                    List.of(key),
                    Long.toString(leaseMillis),
                    token
            );
            if (!Long.valueOf(ACQUIRED).equals(renewed)) {
                log.warn("event: REDIS_CONCURRENCY_LEASE_RENEW_REJECTED domain: {} business: {}",
                        domain, business);
            }
        } catch (RuntimeException exception) {
            log.warn("event: REDIS_CONCURRENCY_LEASE_RENEW_FAILED domain: {} business: {} exceptionType: {}",
                    domain, business, exception.getClass().getSimpleName());
        }
    }
}
