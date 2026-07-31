package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiJwtReplayProtectionService
 * @date : 2026-06-02 11:14
 * @email : scott_x@163.com
 * @description : Open API JWT Replay Protection Service 服务契约，位于 商户开放接口服务，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
public class OpenApiJwtReplayProtectionService {

    /**
     * JWT 过期时间之外额外保留的秒数，用于覆盖轻微时钟漂移和请求在链路中的传输时间。
     */
    private static final long REPLAY_TTL_BUFFER_SECONDS = 60L;

    /**
     * 本地无 Redis 时的最小 TTL 秒数，仅用于计算保护，避免负数 TTL。
     */
    private static final long MIN_TTL_SECONDS = 1L;

    /**
     * StringRedisTemplate 防重放存储依赖。只有显式关闭强制保护的环境才允许缺失。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 是否强制要求 Redis 防重放成功。生产环境建议配置为 true，本地测试可保持 false。
     */
    private final boolean replayRequired;

    /**
     * 支付系统 Redis Key 配置。
     */
    private final PaymentRedisProperties redisProperties;

    /**
     * Redis 降级日志标记，避免 Redis 不可用时每个请求都重复打印警告。
     */
    private final AtomicBoolean redisFallbackWarned = new AtomicBoolean(false);

    /**
     * 创建 OpenAPI JWT 防重放服务。
     *
     * @param stringRedisTemplateProvider Redis 字符串模板提供器
     * @param replayRequired              是否强制要求 Redis 防重放成功
     * @param redisProperties             Redis Key 配置
     */
    public OpenApiJwtReplayProtectionService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                             @Value("${openapi.security.replay.required:false}") boolean replayRequired,
                                             PaymentRedisProperties redisProperties) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.replayRequired = replayRequired;
        this.redisProperties = redisProperties;
        if (replayRequired && this.stringRedisTemplate == null) {
            throw new IllegalStateException("OpenAPI JWT replay protection requires StringRedisTemplate");
        }
        if (this.stringRedisTemplate == null) {
            log.warn("event: OPENAPI_REPLAY_PROTECTION_DEGRADED reason: STRING_REDIS_TEMPLATE_MISSING "
                    + "replayRequired: false");
        }
    }

    /**
     * 校验并记录 JWT jti。
     * <p>
     * 当 Redis 可用时，同一个商户号下同一个 jti 只能成功写入一次；重复写入会被视为重放请求。当 Redis 未配置或
     * 本地 Redis 不可连接时，是否拒绝请求由 openapi.security.replay.required 控制。
     *
     * @param merchantId 商户号
     * @param jwtId      JWT Payload 中的 jti
     * @param expiresAt  JWT 过期秒级时间戳
     */
    public void checkAndMark(String merchantId, String jwtId, long expiresAt) {
        if (!StringUtils.hasText(merchantId) || !StringUtils.hasText(jwtId)) {
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_INVALID);
        }
        if (stringRedisTemplate == null) {
            return;
        }
        String replayKey = buildReplayKey(merchantId, jwtId);
        long ttlSeconds = calculateTtlSeconds(expiresAt);
        Boolean firstRequest;
        try {
            firstRequest = stringRedisTemplate.opsForValue()
                    .setIfAbsent(replayKey, String.valueOf(Instant.now().toEpochMilli()), Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException exception) {
            handleRedisUnavailable(merchantId, exception.getClass().getSimpleName());
            return;
        }
        if (firstRequest == null) {
            handleRedisUnavailable(merchantId, "UNKNOWN_WRITE_RESULT");
            return;
        }
        if (Boolean.FALSE.equals(firstRequest)) {
            log.warn("开放接口JWT防重放命中，商户号：{}，jti摘要长度：{}",
                    merchantId,
                    jwtId.length());
            throw new ApiException(ApiResultEnum.AUTHORIZATION_JWT_INVALID);
        }
    }

    /**
     * 处理 Redis 不可用场景。
     *
     * @param merchantId 商户号
     * @param failureType Redis 失败类型，不包含连接地址或敏感 Key
     */
    private void handleRedisUnavailable(String merchantId, String failureType) {
        if (replayRequired) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        if (redisFallbackWarned.compareAndSet(false, true)) {
            log.warn("开放接口JWT防重放Redis暂不可用，本地降级为仅校验JWT本身，商户号：{}，错误类型：{}",
                    merchantId,
                    failureType);
        }
    }

    /**
     * 构建防重放 Redis Key。
     *
     * @param merchantId 商户号
     * @param jwtId      JWT jti
     * @return Redis Key
     */
    private String buildReplayKey(String merchantId, String jwtId) {
        return redisProperties.key(
                "security",
                "openapi",
                "jwt-replay",
                merchantId,
                RedisKeyDigest.sha256(jwtId)
        );
    }

    /**
     * 计算 Redis 防重放 Key 的 TTL。
     *
     * @param expiresAt JWT 过期秒级时间戳
     * @return Redis TTL 秒数
     */
    private long calculateTtlSeconds(long expiresAt) {
        long nowEpochSeconds = System.currentTimeMillis() / 1000L;
        long ttlSeconds = expiresAt - nowEpochSeconds + REPLAY_TTL_BUFFER_SECONDS;
        return Math.max(MIN_TTL_SECONDS, ttlSeconds);
    }
}
