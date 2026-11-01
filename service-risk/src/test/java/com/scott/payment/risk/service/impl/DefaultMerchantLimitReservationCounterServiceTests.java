package com.scott.payment.risk.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 累计限额 Redis 投影测试，验证同槽 Key、单投影回滚和异常三态。
 */
class DefaultMerchantLimitReservationCounterServiceTests {

    /**
     * 验证累计限额投影通过同槽键原子回滚。
     */
    @Test
    void shouldRollbackClusterSafeProjectionWithCoLocatedKeys() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList())).thenReturn(1L);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:dev");
        DefaultMerchantLimitReservationCounterService service =
                new DefaultMerchantLimitReservationCounterService(redisTemplate, properties);

        assertThat(service.rollback(reservation("CLUSTER_SAFE"))).isTrue();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                keysCaptor.capture());
        assertThat(keysCaptor.getAllValues()).allSatisfy(keys -> {
            assertThat(keys).hasSize(2);
            assertThat(String.valueOf(keys.get(0)))
                    .startsWith("acquiring:dev:risk:")
                    .doesNotContain("service-risk", ":v1:");
            assertThat(String.valueOf(keys.get(1)))
                    .doesNotContain("TX202607301000000000001");
        });
        List<String> clusterKeys = keysCaptor.getValue().stream()
                .map(String::valueOf)
                .toList();
        assertThat(clusterKeys).hasSize(2);
        assertThat(hashTag(clusterKeys.get(0))).isEqualTo(hashTag(clusterKeys.get(1)));
    }

    /**
     * 验证 Lua 返回空结果时按回滚失败处理。
     */
    @Test
    void shouldTreatNullLuaResultAsRollbackFailure() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList())).thenReturn(null);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:dev");
        DefaultMerchantLimitReservationCounterService service =
                new DefaultMerchantLimitReservationCounterService(redisTemplate, properties);

        assertThat(service.rollback(reservation("CLUSTER_SAFE"))).isFalse();
    }

    /**
     * 验证未知计数投影模式被明确拒绝，避免错误补偿。
     */
    @Test
    void shouldRejectUnknownCounterProjection() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        DefaultMerchantLimitReservationCounterService service =
                new DefaultMerchantLimitReservationCounterService(redisTemplate, properties);

        assertThat(service.rollback(reservation("LEGACY"))).isFalse();
    }

    /**
     * 构造具有指定状态、版本和计数投影的预占事实。
     */
    private MerchantLimitReservationDO reservation(String mode) {
        MerchantLimitReservationDO reservation = new MerchantLimitReservationDO();
        reservation.setTransactionId("TX202607301000000000001");
        reservation.setMerchantId("M200001");
        reservation.setRuleId(11L);
        reservation.setLimitType("DAILY");
        reservation.setCurrency("USD");
        reservation.setPeriodBucket("20260730");
        reservation.setCounterMode(mode);
        return reservation;
    }

    /**
     * 提取 Redis 键哈希标签，验证多键脚本使用同一集群槽位。
     */
    private String hashTag(String key) {
        int start = key.indexOf('{');
        int end = key.indexOf('}', start + 1);
        return key.substring(start + 1, end);
    }
}
