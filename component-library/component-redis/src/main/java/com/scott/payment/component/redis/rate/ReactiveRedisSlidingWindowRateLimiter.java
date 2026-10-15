package com.scott.payment.component.redis.rate;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

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
 * @classname : ReactiveRedisSlidingWindowRateLimiter
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : WebFlux 服务共用的 Redis 精确滑动窗口限流器，Redis 异常时降级到有界本地窗口
 * @status : create
 */
@Slf4j
public class ReactiveRedisSlidingWindowRateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final PaymentRedisProperties redisProperties;
    private final LocalSlidingWindowRateLimiter localFallback;
    private final RateLimitFailurePolicy failurePolicy;
    private final AtomicLong lastWarningMillis = new AtomicLong();

    public ReactiveRedisSlidingWindowRateLimiter(ReactiveStringRedisTemplate redisTemplate,
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
     * 使用 Redis 服务端时间原子清理、计数并写入一次请求；Redis 故障时在当前网关实例内继续保护资源。
     *
     * @param scope    限流业务范围，不允许为空
     * @param identity 限流身份原文，只用于本地摘要，不写入 Redis Key 或日志
     * @param limit    一个窗口允许通过的请求数
     * @param window   精确滚动窗口时长
     * @return 异步限流结果；拒绝时包含最早可重试秒数
     */
    public Mono<SlidingWindowRateLimitResult> tryAcquire(String scope,
                                                         String identity,
                                                         int limit,
                                                         Duration window) {
        requireArguments(scope, identity, limit, window);
        String identityDigest = digest(identity);
        String localIdentity = scope + ':' + identityDigest;
        String key = redisProperties.businessKey("rateLimit", scope, identityDigest);
        List<String> arguments = List.of(
                String.valueOf(limit),
                String.valueOf(window.toMillis()),
                UUID.randomUUID().toString()
        );
        return redisTemplate.execute(
                        PaymentRedisScripts.slidingWindowRateLimitV1(),
                        List.of(key),
                        arguments
                )
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Sliding window rate limit returned an empty result")))
                .map(RedisSlidingWindowRateLimiter::parse)
                .onErrorResume(exception -> {
                    warnFailure(scope, exception);
                    if (failurePolicy == RateLimitFailurePolicy.FAIL_CLOSED) {
                        return Mono.just(SlidingWindowRateLimitResult.rejected(retryAfterSeconds(window)));
                    }
                    return Mono.fromSupplier(() -> localFallback.tryAcquire(localIdentity, limit, window));
                });
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

    /** Redis 故障期间每分钟最多记录一次策略事件，避免入口流量放大异常日志。 */
    private void warnFailure(String scope, Throwable exception) {
        long now = System.currentTimeMillis();
        long previous = lastWarningMillis.get();
        if (now - previous >= 60_000L && lastWarningMillis.compareAndSet(previous, now)) {
            log.warn("event: REDIS_RATE_LIMIT_FAILURE scope: {} failurePolicy: {} exceptionType: {}",
                    scope, failurePolicy, exception.getClass().getSimpleName(), exception);
        }
    }
}
