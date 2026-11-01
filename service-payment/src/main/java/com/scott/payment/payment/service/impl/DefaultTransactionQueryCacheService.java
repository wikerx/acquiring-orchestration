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
 * 商户交易查询的可降级缓存服务。
 *
 * <p>Redis 只承担查询减压，不是交易事实来源；任何缓存异常都回源数据库。订单 generation
 * 负责失效旧缓存并阻止并发慢查询回填过期结果。缓存内容为明文 JSON，因此写入前必须阻断
 * 卡号、CVV、有效期等卡认证字段。</p>
 */
@Slf4j
@Service
public class DefaultTransactionQueryCacheService implements TransactionQueryCacheService {

    /**
     * 查询结果基础 TTL 为三天。
     */
    static final long BASE_TTL_SECONDS = Duration.ofDays(3).toSeconds();
    /**
     * 最多增加一天随机抖动，避免同批缓存集中失效。
     */
    static final long MAX_JITTER_SECONDS = Duration.ofDays(1).toSeconds();
    /**
     * generation 比查询缓存多保留一天，确保旧代际不会在结果过期前重新生效。
     */
    static final Duration GENERATION_TTL = Duration.ofDays(5);

    /**
     * 缓存结构变更时升级版本，避免新旧 JSON 契约共用同一 Key。
     */
    private static final String CACHE_SCHEMA_VERSION = "v1";
    /**
     * 尚未发生失效操作的订单使用初始代际。
     */
    private static final String INITIAL_GENERATION = "0";
    /**
     * 未指定平台交易号时，缓存商户订单维度的查询结果。
     */
    private static final String ORDER_QUERY_VARIANT = "order";

    /**
     * 禁止任何请求嵌套对象把卡认证字段带入明文查询缓存。
     */
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

    /**
     * 按商户订单和查询代次读取交易缓存，未命中时回源数据库。
     *
     * <p>数据库回源期间若代次发生变化则放弃回填，避免状态变更后的旧结果重新进入缓存。</p>
     */
    @Override
    public PaymentQueryResultDTO getOrLoad(PaymentCreateCommandDTO commandDTO,
                                           Supplier<PaymentQueryResultDTO> databaseLoader) {
        if (!cacheable(commandDTO) || redisStringService.isEmpty()) {
            return databaseLoader.get();
        }
        RedisStringService redis = redisStringService.get();
        // 读取前冻结 generation，数据库回源后只有代际未变化才允许回填。
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

    /**
     * 原子推进商户订单查询代次，使此前生成的交易查询缓存立即失效。
     */
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
            // 递增代际使所有旧 Key 立即失效，无需扫描删除不同查询维度的缓存。
            redis.increment(key, 1L);
            return redis.expire(key, GENERATION_TTL);
        } catch (RuntimeException exception) {
            log.warn("交易查询缓存失效降级, event: TRANSACTION_QUERY_CACHE_INVALIDATION_FALLBACK, exceptionType: {}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Redis 读取、类型或 JSON 异常只造成缓存未命中。
     */
    private PaymentQueryResultDTO read(RedisStringService redis, String cacheKey) {
        try {
            Object value = redis.get(cacheKey);
            if (value == null) {
                return null;
            }
            if (!(value instanceof String json) || !StringUtils.hasText(json)) {
                // 非字符串或空内容不符合缓存契约，删除后按未命中回源。
                redis.delete(cacheKey);
                return null;
            }
            return JsonUtils.parseObject(json, PaymentQueryResultDTO.class);
        } catch (RuntimeException exception) {
            log.warn("交易查询缓存读取降级, event: TRANSACTION_QUERY_CACHE_READ_FALLBACK, exceptionType: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 仅当加载前后 generation 相同且 JSON 不含卡认证字段时回填。
     */
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
            // 回源期间若交易状态已推进，丢弃本次旧快照，防止覆盖新代际查询结果。
            if (!expectedGeneration.equals(currentGeneration)) {
                return;
            }
            String json = JsonUtils.toJsonString(loaded);
            if (FORBIDDEN_CARD_FIELD.matcher(json).find()) {
                log.error("交易查询缓存写入被拒绝, event: TRANSACTION_QUERY_CACHE_WRITE_REJECTED, reason: forbiddenCardField");
                return;
            }
            redis.set(cacheKey, json, jitteredTransactionTtl());
        } catch (RuntimeException exception) {
            log.warn("交易查询缓存写入降级, event: TRANSACTION_QUERY_CACHE_WRITE_FALLBACK, exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 读取当前订单 generation；不存在使用零，Redis 异常返回 null 触发数据库降级。
     */
    private String currentGeneration(RedisStringService redis, String merchantId, String merchantOrderNo) {
        try {
            Object value = redis.get(generationKey(merchantId, merchantOrderNo));
            return value == null ? INITIAL_GENERATION : String.valueOf(value);
        } catch (RuntimeException exception) {
            log.warn("交易查询缓存代际读取降级, event: TRANSACTION_QUERY_GENERATION_READ_FALLBACK, exceptionType: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 构建交易查询缓存键。
     */
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

    /**
     * 构建代次键。
     */
    private String generationKey(String merchantId, String merchantOrderNo) {
        return redisProperties.businessKey(
                "payment",
                "transaction-query-generation",
                CACHE_SCHEMA_VERSION,
                RedisKeyDigest.sha256(merchantId.trim()),
                RedisKeyDigest.sha256(merchantOrderNo.trim())
        );
    }

    /**
     * 判断交易查询结果是否允许写入缓存。
     */
    private boolean cacheable(PaymentCreateCommandDTO commandDTO) {
        return commandDTO != null
                && StringUtils.hasText(commandDTO.getMerchantId())
                && StringUtils.hasText(commandDTO.getMerchantOrderNo());
    }

    /**
     * 计算带随机抖动的交易缓存过期时间。
     */
    static Duration jitteredTransactionTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(MAX_JITTER_SECONDS + 1L);
        return Duration.ofSeconds(BASE_TTL_SECONDS + jitterSeconds);
    }
}
