package com.scott.payment.component.redis.security;

import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisInternalRequestReplayGuard
 * @date : 2026-08-20 23:50
 * @email : scott_x@163.com
 * @description : 使用带环境前缀的 Redis SET NX 原子占用内部服务 nonce，Redis 异常时保持失败关闭
 * @status : create
 */
public class RedisInternalRequestReplayGuard implements InternalRequestReplayGuard {

    /** Redis 字符串操作入口。 */
    private final StringRedisTemplate redisTemplate;

    /** 统一 Redis 物理 Key 构造器。 */
    private final PaymentRedisKeyResolver keyResolver;

    /**
     * 创建 Redis 内部请求防重放守卫。
     *
     * @param redisTemplate Redis 字符串操作入口
     * @param keyResolver 统一 Redis 物理 Key 构造器
     */
    public RedisInternalRequestReplayGuard(StringRedisTemplate redisTemplate,
                                           PaymentRedisKeyResolver keyResolver) {
        this.redisTemplate = redisTemplate;
        this.keyResolver = keyResolver;
    }

    /** {@inheritDoc} */
    @Override
    public boolean tryAcquire(String caller, String nonce, Duration ttl) {
        if (!StringUtils.hasText(caller) || !StringUtils.hasText(nonce)) {
            throw new IllegalArgumentException("internal request replay identity is required");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("internal request replay ttl must be positive");
        }
        String key = keyResolver.businessKey(
                "security",
                "internal-nonce",
                caller.trim(),
                RedisKeyDigest.sha256(nonce.trim()));
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        if (acquired == null) {
            throw new IllegalStateException("internal request replay guard returned no result");
        }
        return acquired;
    }
}
