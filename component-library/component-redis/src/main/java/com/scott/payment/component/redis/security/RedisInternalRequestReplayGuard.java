package com.scott.payment.component.redis.security;

import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 使用带环境前缀的 Redis SET NX 原子占用内部服务 nonce，Redis 异常时保持失败关闭
 */
public class RedisInternalRequestReplayGuard implements InternalRequestReplayGuard {

    /**
     * Redis 字符串操作入口。
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 统一 Redis 物理 Key 构造器。
     */
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

    /**
     * 使用 Redis 原子 SET NX 为已验签调用方占用 nonce。
     * Redis 无法给出确定结果时抛出异常，由入口按失败策略拒绝请求。
     *
     * @param caller 已通过 HMAC 验证的调用方服务标识
     * @param nonce 已通过格式校验的请求随机串；Redis Key 仅保存其摘要
     * @param ttl 防重放记录有效期，必须为正数
     * @return 首次占用时返回 {@code true}，重复请求返回 {@code false}
     */
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
