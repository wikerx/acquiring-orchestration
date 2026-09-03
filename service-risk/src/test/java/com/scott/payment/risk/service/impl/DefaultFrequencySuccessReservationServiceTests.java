package com.scott.payment.risk.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.domain.FrequencySuccessReservationResult;
import com.scott.payment.risk.domain.FrequencySuccessReservationTransitionSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultFrequencySuccessReservationServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 频控成功名额 Redis 生命周期测试。
 * @status : create
 */
class DefaultFrequencySuccessReservationServiceTests {

    @Test
    void shouldReserveWithDigestOnlyKeysInOneMerchantSlot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), eq("3"), eq("3600")))
                .thenReturn(List.of(1L, 1L));
        DefaultFrequencySuccessReservationService service = service(redisTemplate);

        FrequencySuccessReservationResult result = service.reserve(
                "merchant-raw-001",
                "transaction-raw-001",
                21L,
                "element-raw-value",
                3,
                3600);

        assertThat(result.outcome())
                .isEqualTo(FrequencySuccessReservationResult.Outcome.RESERVED);
        assertThat(result.currentCount()).isEqualTo(1L);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(), keysCaptor.capture(), eq("3"), eq("3600"));
        List<?> keys = keysCaptor.getValue();
        assertThat(keys).hasSize(2);
        assertThat(hashTag(String.valueOf(keys.get(0))))
                .isEqualTo(hashTag(String.valueOf(keys.get(1))));
        assertThat(keys).allSatisfy(key -> assertThat(String.valueOf(key))
                .doesNotContain("merchant-raw-001", "transaction-raw-001", "element-raw-value"));
    }

    @Test
    void shouldReportLimitExceededWithoutClaimingReservation() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), eq("1"), eq("3600")))
                .thenReturn(List.of(0L, 1L));
        DefaultFrequencySuccessReservationService service = service(redisTemplate);

        FrequencySuccessReservationResult result = service.reserve(
                "M001", "TX002", 22L, "element-digest", 1, 3600);

        assertThat(result.outcome())
                .isEqualTo(FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED);
        assertThat(result.currentCount()).isEqualTo(1L);
    }

    @Test
    void shouldReportUnavailableWhenRedisReserveExecutionFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), eq("2"), eq("3600")))
                .thenThrow(new IllegalStateException("simulated redis failure"));
        DefaultFrequencySuccessReservationService service = service(redisTemplate);

        FrequencySuccessReservationResult result = service.reserve(
                "M001", "TX-REDIS-FAILED", 23L, "ip-digest", 2, 3600);

        assertThat(result).isEqualTo(FrequencySuccessReservationResult.unavailable());
    }

    @Test
    void shouldConfirmOnceAndTreatDuplicateSuccessAsIdempotent() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), eq("CONFIRM")))
                .thenReturn(List.of(1L), List.of(2L));
        DefaultFrequencySuccessReservationService service = service(redisTemplate);

        FrequencySuccessReservationTransitionSummary first =
                service.confirm("M001", "TX003");
        FrequencySuccessReservationTransitionSummary duplicate =
                service.confirm("M001", "TX003");

        assertThat(first).isEqualTo(new FrequencySuccessReservationTransitionSummary(1, 0, 0));
        assertThat(duplicate).isEqualTo(new FrequencySuccessReservationTransitionSummary(0, 1, 0));
    }

    @Test
    void shouldReleaseReservedCountersAndKeepDuplicateFailureIdempotent() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PaymentRedisProperties properties = redisProperties();
        String merchantSlot = RedisKeyDigest.sha256("M001");
        String firstCounter = properties.coLocatedBusinessKey(
                "risk", "frequency-success", merchantSlot,
                "rule", "21", "element", RedisKeyDigest.sha256("ip-hash"), "counter");
        String secondCounter = properties.coLocatedBusinessKey(
                "risk", "frequency-success", merchantSlot,
                "rule", "22", "element", RedisKeyDigest.sha256("card-hash"), "counter");
        AtomicInteger transitions = new AtomicInteger();
        when(redisTemplate.execute(any(), anyList(), eq("RELEASE")))
                .thenAnswer(invocation -> transitions.getAndIncrement() == 0
                        ? List.of(1L, firstCounter, secondCounter)
                        : List.of(3L));
        when(redisTemplate.execute(any(), anyList())).thenReturn(2L);
        DefaultFrequencySuccessReservationService service =
                new DefaultFrequencySuccessReservationService(redisTemplate, properties);

        FrequencySuccessReservationTransitionSummary first =
                service.release("M001", "TX004");
        FrequencySuccessReservationTransitionSummary duplicate =
                service.release("M001", "TX004");

        assertThat(first).isEqualTo(new FrequencySuccessReservationTransitionSummary(2, 0, 0));
        assertThat(duplicate).isEqualTo(new FrequencySuccessReservationTransitionSummary(0, 1, 0));
    }

    @Test
    void shouldNotReleaseAfterSuccessWasConfirmed() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), eq("RELEASE")))
                .thenReturn(List.of(-1L));
        DefaultFrequencySuccessReservationService service = service(redisTemplate);

        FrequencySuccessReservationTransitionSummary result =
                service.release("M001", "TX005");

        assertThat(result).isEqualTo(new FrequencySuccessReservationTransitionSummary(0, 0, 1));
    }

    private DefaultFrequencySuccessReservationService service(StringRedisTemplate redisTemplate) {
        return new DefaultFrequencySuccessReservationService(redisTemplate, redisProperties());
    }

    private PaymentRedisProperties redisProperties() {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return properties;
    }

    private String hashTag(String key) {
        return key.substring(key.indexOf('{') + 1, key.indexOf('}'));
    }
}
