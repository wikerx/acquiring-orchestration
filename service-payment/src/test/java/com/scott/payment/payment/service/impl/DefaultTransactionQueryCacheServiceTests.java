package com.scott.payment.payment.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.string.RedisStringService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionQueryCacheServiceTests
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 验证交易查询缓存的命中、失效竞态、敏感字段边界、TTL 和 Redis 故障降级契约。
 * @status : create
 */
class DefaultTransactionQueryCacheServiceTests {

    @Test
    void shouldCacheDatabaseResultAndRestoreCurrentRequestId() {
        InMemoryRedisStringService redis = new InMemoryRedisStringService();
        DefaultTransactionQueryCacheService service = service(redis);
        AtomicInteger loads = new AtomicInteger();

        PaymentQueryResultDTO first = service.getOrLoad(command("request-1"), () -> {
            loads.incrementAndGet();
            return result("request-1");
        });
        PaymentQueryResultDTO second = service.getOrLoad(command("request-2"), () -> {
            loads.incrementAndGet();
            return result("request-2");
        });

        assertThat(first.getMerchantOrderId()).isEqualTo("request-1");
        assertThat(second.getMerchantOrderId()).isEqualTo("request-2");
        assertThat(loads).hasValue(1);
        assertThat(redis.lastCacheTtl.getSeconds())
                .isBetween(DefaultTransactionQueryCacheService.BASE_TTL_SECONDS,
                        DefaultTransactionQueryCacheService.BASE_TTL_SECONDS
                                + DefaultTransactionQueryCacheService.MAX_JITTER_SECONDS);
        assertThat(redis.lastCacheValue)
                .doesNotContain("cardNo", "cardNumber", "securityCode", "cvv", "cvc",
                        "expirationMonth", "expirationYear", "expiryMonth", "expiryYear");
    }

    @Test
    void shouldNotWriteStaleResultWhenGenerationChangesDuringDatabaseLoad() {
        InMemoryRedisStringService redis = new InMemoryRedisStringService();
        DefaultTransactionQueryCacheService service = service(redis);

        PaymentQueryResultDTO loaded = service.getOrLoad(command("request-1"), () -> {
            assertThat(service.advanceGeneration("merchant-1", "order-1")).isTrue();
            return result("request-1");
        });

        assertThat(loaded.getMerchantOrderId()).isEqualTo("request-1");
        assertThat(redis.lastCacheValue).isNull();
    }

    @Test
    void shouldFallBackToDatabaseWhenRedisReadFails() {
        InMemoryRedisStringService redis = new InMemoryRedisStringService();
        redis.failReads = true;
        DefaultTransactionQueryCacheService service = service(redis);

        PaymentQueryResultDTO loaded = service.getOrLoad(command("request-1"),
                () -> result("request-1"));

        assertThat(loaded.getMerchantOrderId()).isEqualTo("request-1");
        assertThat(redis.lastCacheValue).isNull();
    }

    @Test
    void shouldRejectRuntimeSubtypeThatWouldExposeCardCredentialFields() {
        InMemoryRedisStringService redis = new InMemoryRedisStringService();
        DefaultTransactionQueryCacheService service = service(redis);

        service.getOrLoad(command("request-1"), SensitiveQueryResult::new);

        assertThat(redis.lastCacheValue).isNull();
    }

    private DefaultTransactionQueryCacheService service(RedisStringService redis) {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return new DefaultTransactionQueryCacheService(Optional.of(redis), properties);
    }

    private PaymentCreateCommandDTO command(String requestId) {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId("merchant-1");
        command.setMerchantOrderNo("order-1");
        command.setMerchantOrderId(requestId);
        return command;
    }

    private PaymentQueryResultDTO result(String requestId) {
        PaymentQueryResultDTO result = new PaymentQueryResultDTO();
        result.setMerchantId("merchant-1");
        result.setMerchantOrderNo("order-1");
        result.setMerchantOrderId(requestId);
        PaymentQueryResultDTO.TransactionInfoDTO transaction = new PaymentQueryResultDTO.TransactionInfoDTO();
        transaction.setTransactionId("transaction-1");
        transaction.setCardBin("123456****7890");
        result.getTransactionInfo().add(transaction);
        return result;
    }

    private static final class SensitiveQueryResult extends PaymentQueryResultDTO {

        public String getCardNo() {
            return "prohibited";
        }
    }

    private static final class InMemoryRedisStringService implements RedisStringService {

        private final Map<String, Object> values = new HashMap<>();
        private boolean failReads;
        private String lastCacheValue;
        private Duration lastCacheTtl;

        @Override
        public void set(String key, Object value) {
            values.put(key, value);
        }

        @Override
        public void set(String key, Object value, Duration ttl) {
            values.put(key, value);
            if (key.contains(":transaction-query:")) {
                lastCacheValue = String.valueOf(value);
                lastCacheTtl = ttl;
            }
        }

        /**
         * 模拟缓存 SETNX，只允许首个值占用给定键。
         * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
         * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
         * @param ttl 测试记录的缓存有效期
         * @return 首次写入时返回 true，键已存在时返回 false
         */
        @Override
        public boolean setIfAbsent(String key, Object value, Duration ttl) {
            return values.putIfAbsent(key, value) == null;
        }

        @Override
        public Object get(String key) {
            if (failReads) {
                throw new IllegalStateException("redis unavailable");
            }
            return values.get(key);
        }

        @Override
        public <T> T get(String key, Class<T> type) {
            return type.cast(get(key));
        }

        @Override
        public boolean hasKey(String key) {
            return values.containsKey(key);
        }

        @Override
        public boolean expire(String key, Duration ttl) {
            return values.containsKey(key);
        }

        @Override
        public long getExpireSeconds(String key) {
            return -1L;
        }

        @Override
        public boolean delete(String key) {
            return values.remove(key) != null;
        }

        @Override
        public long increment(String key, long delta) {
            long next = Long.parseLong(String.valueOf(values.getOrDefault(key, 0L))) + delta;
            values.put(key, next);
            return next;
        }

        @Override
        public long decrement(String key, long delta) {
            return increment(key, -delta);
        }
    }
}
