package com.scott.payment.risk.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.risk.domain.FrequencySuccessReservationResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultFrequencySuccessReservationServiceRedisIntegrationTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 真实 Redis 下频控成功名额脚本的原子性和终态幂等集成测试。
 * @status : create
 */
@EnabledIfSystemProperty(named = "risk.redis.standalone.integration.enabled", matches = "true")
class DefaultFrequencySuccessReservationServiceRedisIntegrationTests {

    /** 隔离 Redis 集成测试使用的连接工厂。 */
    private static LettuceConnectionFactory connectionFactory;

    /** 使用真实 Lua 脚本执行预留、确认和释放的被测服务。 */
    private static DefaultFrequencySuccessReservationService service;

    @BeforeAll
    static void setUpRedis() {
        int port = Integer.parseInt(System.getProperty("risk.redis.standalone.port"));
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:frequency-success-it");
        service = new DefaultFrequencySuccessReservationService(redisTemplate, properties);
    }

    @AfterAll
    static void closeRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldReserveReleaseConfirmAndProtectTerminalState() {
        String merchantId = "merchant-" + UUID.randomUUID();
        FrequencySuccessReservationResult first =
                service.reserve(merchantId, "TX-1", 101L, "ip-digest", 1, 60);
        FrequencySuccessReservationResult duplicate =
                service.reserve(merchantId, "TX-1", 101L, "ip-digest", 1, 60);
        FrequencySuccessReservationResult full =
                service.reserve(merchantId, "TX-2", 101L, "ip-digest", 1, 60);

        assertThat(first.outcome()).isEqualTo(FrequencySuccessReservationResult.Outcome.RESERVED);
        assertThat(duplicate.outcome()).isEqualTo(FrequencySuccessReservationResult.Outcome.IDEMPOTENT);
        assertThat(full.outcome()).isEqualTo(FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED);
        assertThat(service.release(merchantId, "TX-1").applied()).isEqualTo(1);
        assertThat(service.release(merchantId, "TX-1").idempotent()).isEqualTo(1);

        FrequencySuccessReservationResult reused =
                service.reserve(merchantId, "TX-2", 101L, "ip-digest", 1, 60);
        assertThat(reused.outcome()).isEqualTo(FrequencySuccessReservationResult.Outcome.RESERVED);
        assertThat(service.confirm(merchantId, "TX-2").applied()).isEqualTo(1);
        assertThat(service.confirm(merchantId, "TX-2").idempotent()).isEqualTo(1);
        assertThat(service.release(merchantId, "TX-2").conflicted()).isEqualTo(1);
        assertThat(service.reserve(merchantId, "TX-3", 101L, "ip-digest", 1, 60).outcome())
                .isEqualTo(FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED);
    }

    @Test
    void shouldNeverReserveMoreThanSuccessLimitUnderConcurrency() throws Exception {
        String merchantId = "merchant-concurrent-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<FrequencySuccessReservationResult>> tasks = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                String transactionId = "TX-C-" + index;
                tasks.add(() -> service.reserve(
                        merchantId, transactionId, 102L, "card-digest", 3, 60));
            }
            List<Future<FrequencySuccessReservationResult>> futures = executor.invokeAll(tasks);
            List<FrequencySuccessReservationResult> results = new ArrayList<>();
            for (Future<FrequencySuccessReservationResult> future : futures) {
                results.add(future.get());
            }

            assertThat(results).filteredOn(result ->
                    result.outcome() == FrequencySuccessReservationResult.Outcome.RESERVED)
                    .hasSize(3);
            assertThat(results).filteredOn(result ->
                    result.outcome() == FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED)
                    .hasSize(9);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldReleasePartialMultiDimensionReservationAfterLaterDimensionIsFull() {
        String merchantId = "merchant-partial-" + UUID.randomUUID();
        FrequencySuccessReservationResult occupied =
                service.reserve(merchantId, "TX-OCCUPIED", 202L, "card-digest", 1, 60);
        FrequencySuccessReservationResult firstDimension =
                service.reserve(merchantId, "TX-PARTIAL", 201L, "ip-digest", 1, 60);
        FrequencySuccessReservationResult blockedDimension =
                service.reserve(merchantId, "TX-PARTIAL", 202L, "card-digest", 1, 60);

        assertThat(occupied.outcome()).isEqualTo(FrequencySuccessReservationResult.Outcome.RESERVED);
        assertThat(firstDimension.outcome()).isEqualTo(FrequencySuccessReservationResult.Outcome.RESERVED);
        assertThat(blockedDimension.outcome())
                .isEqualTo(FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED);
        assertThat(service.release(merchantId, "TX-PARTIAL").applied()).isEqualTo(1);

        FrequencySuccessReservationResult reused =
                service.reserve(merchantId, "TX-REUSED", 201L, "ip-digest", 1, 60);
        assertThat(reused.outcome()).isEqualTo(FrequencySuccessReservationResult.Outcome.RESERVED);
    }
}
