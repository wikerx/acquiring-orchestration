package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiJwtReplayProtectionService
 * @date : 2026-06-02 11:23
 * @email : scott_x@163.com
 * @description : OpenAPI JWT jti Redis 防重放服务
 * @status : create
 */
@Slf4j
@Component
public class OpenApiJwtReplayProtectionService {

    /**
     * 防重放 Redis Key 前缀，按商户号和 JWT jti 隔离，避免不同商户之间互相影响。
     */
    private static final String JWT_REPLAY_KEY_PREFIX = "payment:openapi:jwt:jti:";

    /**
     * JWT 过期时间之外额外保留的秒数，用于覆盖轻微时钟漂移和请求在链路中的传输时间。
     */
    private static final long REPLAY_TTL_BUFFER_SECONDS = 60L;

    /**
     * 本地无 Redis 时的最小 TTL 秒数，仅用于计算保护，避免负数 TTL。
     */
    private static final long MIN_TTL_SECONDS = 1L;

    /**
     * StringRedisTemplate 可选依赖。部分单元测试或网关服务不需要 Redis，缺失时不阻塞服务启动。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 是否强制要求 Redis 防重放成功。生产环境建议配置为 true，本地测试可保持 false。
     */
    private final boolean replayRequired;

    /**
     * Redis 降级日志标记，避免 Redis 不可用时每个请求都重复打印警告。
     */
    private final AtomicBoolean redisFallbackWarned = new AtomicBoolean(false);

    /**
     * 创建 OpenAPI JWT 防重放服务。
     *
     * @param stringRedisTemplateProvider Redis 字符串模板提供器
     * @param replayRequired              是否强制要求 Redis 防重放成功
     */
    public OpenApiJwtReplayProtectionService(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                             @Value("${openapi.security.replay.required:false}") boolean replayRequired) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
        this.replayRequired = replayRequired;
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
        if (stringRedisTemplate == null) {
            return;
        }
        if (!StringUtils.hasText(merchantId) || !StringUtils.hasText(jwtId)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
        String replayKey = buildReplayKey(merchantId, jwtId);
        long ttlSeconds = calculateTtlSeconds(expiresAt);
        Boolean firstRequest;
        try {
            firstRequest = stringRedisTemplate.opsForValue()
                    .setIfAbsent(replayKey, "1", Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException exception) {
            handleRedisFailure(merchantId, exception);
            return;
        }
        if (Boolean.FALSE.equals(firstRequest)) {
            log.warn("开放接口JWT防重放命中，商户号：{}，jti摘要长度：{}，RedisKey前缀：{}",
                    merchantId,
                    jwtId.length(),
                    JWT_REPLAY_KEY_PREFIX);
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT);
        }
    }

    /**
     * 处理 Redis 不可用场景。
     *
     * @param merchantId 商户号
     * @param exception  Redis 访问异常
     */
    private void handleRedisFailure(String merchantId, DataAccessException exception) {
        if (replayRequired) {
            throw new ApiException(ApiCoResultEnum.CO_INTERNAL_SERVER_ERROR);
        }
        if (redisFallbackWarned.compareAndSet(false, true)) {
            log.warn("开放接口JWT防重放Redis暂不可用，本地降级为仅校验JWT本身，商户号：{}，错误类型：{}",
                    merchantId,
                    exception.getClass().getSimpleName());
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
        return JWT_REPLAY_KEY_PREFIX + merchantId + ":" + jwtId;
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
