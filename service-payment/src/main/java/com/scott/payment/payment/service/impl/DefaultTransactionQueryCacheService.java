package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.service.TransactionQueryCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionQueryCacheService
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 交易查询缓存默认实现，以明文 JSON 保存商户查询返回模型，禁止卡号、CVV 和有效期字段进入 Redis，并在故障时回源数据库。
 * @status : create
 */
@Slf4j
@Service
public class DefaultTransactionQueryCacheService implements TransactionQueryCacheService {

    static final long BASE_TTL_SECONDS = Duration.ofDays(3).toSeconds();
    static final long MAX_JITTER_SECONDS = Duration.ofDays(1).toSeconds();
    static final Duration GENERATION_TTL = Duration.ofDays(5);

    private static final String CACHE_SCHEMA_VERSION = "v1";
    private static final String INITIAL_GENERATION = "0";
    private static final String ORDER_QUERY_VARIANT = "order";

    /** 禁止任何请求嵌套对象把卡认证字段带入明文查询缓存。 */
    private static final Pattern FORBIDDEN_CARD_FIELD = Pattern.compile(
            "(?i)\"(?:cardNo|cardNumber|securityCode|cvv|cvc|expirationMonth|expirationYear|expiryMonth|expiryYear)\"\\s*:"
    );

    private final Optional<RedisStringService> redisStringService;
    private final PaymentRedisProperties redisProperties;

    /**
     * 创建可降级交易查询缓存服务。
     *
     * @param redisStringService Redis String 服务；关闭通用 Redis 能力时允许为空
     * @param redisProperties Redis Key 规范配置
     */
    public DefaultTransactionQueryCacheService(Optional<RedisStringService> redisStringService,
                                               PaymentRedisProperties redisProperties) {
        this.redisStringService = redisStringService;
        this.redisProperties = redisProperties;
    }

    /** {@inheritDoc} */
    @Override
    public PaymentQueryResultDTO getOrLoad(PaymentCreateCommandDTO commandDTO,
                                           Supplier<PaymentQueryResultDTO> databaseLoader) {
        if (!cacheable(commandDTO) || redisStringService.isEmpty()) {
            return databaseLoader.get();
        }
        RedisStringService redis = redisStringService.get();
        String generation = currentGeneration(redis, commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
        if (generation == null) {
            return databaseLoader.get();
        }
        String cacheKey = queryCacheKey(commandDTO, generation);
        PaymentQueryResultDTO cached = read(redis, cacheKey);
        if (cached != null) {
            cached.setMerchantOrderId(commandDTO.getMerchantOrderId());
            return cached;
        }

        PaymentQueryResultDTO loaded = databaseLoader.get();
        writeIfGenerationUnchanged(redis, commandDTO, generation, cacheKey, loaded);
        return loaded;
    }

    /** {@inheritDoc} */
    @Override
    public boolean advanceGeneration(String merchantId, String merchantOrderNo) {
        if (!StringUtils.hasText(merchantId)
                || !StringUtils.hasText(merchantOrderNo)
                || redisStringService.isEmpty()) {
            return false;
        }
        try {
            RedisStringService redis = redisStringService.get();
            String key = generationKey(merchantId, merchantOrderNo);
            redis.increment(key, 1L);
            return redis.expire(key, GENERATION_TTL);
        } catch (RuntimeException exception) {
            log.warn("event: TRANSACTION_QUERY_CACHE_INVALIDATION_FALLBACK exceptionType: {}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    /** Redis 读取、类型或 JSON 异常只造成缓存未命中。 */
    private PaymentQueryResultDTO read(RedisStringService redis, String cacheKey) {
        try {
            Object value = redis.get(cacheKey);
            if (value == null) {
                return null;
            }
            if (!(value instanceof String json) || !StringUtils.hasText(json)) {
                redis.delete(cacheKey);
                return null;
            }
            return JsonUtils.parseObject(json, PaymentQueryResultDTO.class);
        } catch (RuntimeException exception) {
            log.warn("event: TRANSACTION_QUERY_CACHE_READ_FALLBACK exceptionType: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    /** 仅当加载前后 generation 相同且 JSON 不含卡认证字段时回填。 */
    private void writeIfGenerationUnchanged(RedisStringService redis,
                                            PaymentCreateCommandDTO commandDTO,
                                            String expectedGeneration,
                                            String cacheKey,
                                            PaymentQueryResultDTO loaded) {
        if (loaded == null) {
            return;
        }
        try {
            String currentGeneration = currentGeneration(
                    redis, commandDTO.getMerchantId(), commandDTO.getMerchantOrderNo());
            if (!expectedGeneration.equals(currentGeneration)) {
                return;
            }
            String json = JsonUtils.toJsonString(loaded);
            if (FORBIDDEN_CARD_FIELD.matcher(json).find()) {
                log.error("event: TRANSACTION_QUERY_CACHE_WRITE_REJECTED reason=forbiddenCardField");
                return;
            }
            redis.set(cacheKey, json, jitteredTransactionTtl());
        } catch (RuntimeException exception) {
            log.warn("event: TRANSACTION_QUERY_CACHE_WRITE_FALLBACK exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /** 读取当前订单 generation；不存在使用零，Redis 异常返回 null 触发数据库降级。 */
    private String currentGeneration(RedisStringService redis, String merchantId, String merchantOrderNo) {
        try {
            Object value = redis.get(generationKey(merchantId, merchantOrderNo));
            return value == null ? INITIAL_GENERATION : String.valueOf(value);
        } catch (RuntimeException exception) {
            log.warn("event: TRANSACTION_QUERY_GENERATION_READ_FALLBACK exceptionType: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private String queryCacheKey(PaymentCreateCommandDTO commandDTO, String generation) {
        String transactionId = commandDTO.getTransactionInfo() == null
                ? null : commandDTO.getTransactionInfo().getTransactionId();
        String variant = StringUtils.hasText(transactionId)
                ? RedisKeyDigest.sha256(transactionId.trim()) : ORDER_QUERY_VARIANT;
        return redisProperties.businessKey(
                "payment",
                "transaction-query",
                CACHE_SCHEMA_VERSION,
                RedisKeyDigest.sha256(commandDTO.getMerchantId().trim()),
                RedisKeyDigest.sha256(commandDTO.getMerchantOrderNo().trim()),
                generation,
                variant
        );
    }

    private String generationKey(String merchantId, String merchantOrderNo) {
        return redisProperties.businessKey(
                "payment",
                "transaction-query-generation",
                CACHE_SCHEMA_VERSION,
                RedisKeyDigest.sha256(merchantId.trim()),
                RedisKeyDigest.sha256(merchantOrderNo.trim())
        );
    }

    private boolean cacheable(PaymentCreateCommandDTO commandDTO) {
        return commandDTO != null
                && StringUtils.hasText(commandDTO.getMerchantId())
                && StringUtils.hasText(commandDTO.getMerchantOrderNo());
    }

    static Duration jitteredTransactionTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(MAX_JITTER_SECONDS + 1L);
        return Duration.ofSeconds(BASE_TTL_SECONDS + jitterSeconds);
    }
}
