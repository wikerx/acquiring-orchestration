package com.scott.payment.component.redis.rate;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisSlidingWindowRateLimiter
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : Servlet 服务共用的 Redis 精确滑动窗口限流器，Redis 异常时降级到有界本地窗口
 * @status : create
 */
@Slf4j
public class RedisSlidingWindowRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final PaymentRedisProperties redisProperties;
    private final LocalSlidingWindowRateLimiter localFallback;
    private final RateLimitFailurePolicy failurePolicy;
    private final AtomicLong lastWarningMillis = new AtomicLong();

    public RedisSlidingWindowRateLimiter(
            StringRedisTemplate redisTemplate,
            PaymentRedisProperties redisProperties,
            int localMaxIdentities,
            RateLimitFailurePolicy failurePolicy) {
        if (failurePolicy == null) {
            throw new IllegalArgumentException("Rate limit failure policy is required");
        }
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
        this.localFallback = new LocalSlidingWindowRateLimiter(localMaxIdentities);
        this.failurePolicy = failurePolicy;
    }

    /**
     * 对指定业务范围和身份执行一次原子限流判定。
     */
    public SlidingWindowRateLimitResult tryAcquire(String scope,
                                                   String identity,
                                                   int limit,
                                                   Duration window) {
        requireArguments(scope, identity, limit, window);
        String identityDigest = digest(identity);
        try {
            List<?> result = redisTemplate.execute(
                    PaymentRedisScripts.slidingWindowRateLimitV1(),
                    List.of(redisProperties.businessKey("rateLimit", scope, identityDigest)),
                    String.valueOf(limit),
                    String.valueOf(window.toMillis()),
                    UUID.randomUUID().toString()
            );
            return parse(result);
        } catch (RuntimeException exception) {
            warnFailure(scope, exception);
            if (failurePolicy == RateLimitFailurePolicy.FAIL_CLOSED) {
                return SlidingWindowRateLimitResult.rejected(retryAfterSeconds(window));
            }
            return localFallback.tryAcquire(scope + ':' + identityDigest, limit, window);
        }
    }

    static SlidingWindowRateLimitResult parse(List<?> result) {
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Sliding window rate limit returned an invalid result");
        }
        long allowed = ((Number) result.get(0)).longValue();
        long retry = ((Number) result.get(1)).longValue();
        return allowed == 1L
                ? SlidingWindowRateLimitResult.permitted()
                : SlidingWindowRateLimitResult.rejected(retry);
    }

    private void requireArguments(String scope, String identity, int limit, Duration window) {
        if (!StringUtils.hasText(scope) || !StringUtils.hasText(identity)
                || limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Sliding window rate limit arguments are invalid");
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long retryAfterSeconds(Duration window) {
        return Math.max(1L, (window.toMillis() + 999L) / 1000L);
    }

    /** Redis 故障期间每分钟最多记录一次策略事件，避免限流流量放大异常日志。 */
    private void warnFailure(String scope, RuntimeException exception) {
        long now = System.currentTimeMillis();
        long previous = lastWarningMillis.get();
        if (now - previous >= 60_000L && lastWarningMillis.compareAndSet(previous, now)) {
            log.warn("event: REDIS_RATE_LIMIT_FAILURE scope: {} failurePolicy: {} exceptionType: {}",
                    scope, failurePolicy, exception.getClass().getSimpleName(), exception);
        }
    }
}
