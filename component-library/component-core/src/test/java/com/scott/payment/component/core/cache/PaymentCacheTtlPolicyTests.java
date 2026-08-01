package com.scott.payment.component.core.cache;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCacheTtlPolicyTests
 * @date : 2026-07-30 21:10
 * @email : scott_x@163.com
 * @description : 验证 Spring Cache 与直连 Redis 共用的 TTL 抖动边界和非法配置拒绝规则
 * @status : create
 */
@Slf4j
class PaymentCacheTtlPolicyTests {

    /**
     * 十个百分点的抖动必须把十分钟基础 TTL 限制在九至十一分钟之间。
     */
    @Test
    void shouldGenerateBoundedTtlJitter() {
        log.info("测试统一 TTL 抖动，基础 TTL 为 10 分钟，抖动比例为 10%");
        Set<Duration> observed = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            Duration ttl = PaymentCacheTtlPolicy.jitter(Duration.ofMinutes(10), 10);
            assertThat(ttl).isBetween(Duration.ofMinutes(9), Duration.ofMinutes(11));
            observed.add(ttl);
        }

        assertThat(observed).hasSizeGreaterThan(1);
        log.info("统一 TTL 抖动验证完成，样本数量: {}", observed.size());
    }

    /**
     * 非正基础 TTL 和超出安全区间的抖动比例必须在写入 Redis 前失败。
     */
    @Test
    void shouldRejectUnsafeTtlConfiguration() {
        log.info("测试统一 TTL 策略拒绝非正 TTL 和越界抖动比例");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentCacheTtlPolicy.jitter(Duration.ZERO, 10));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentCacheTtlPolicy.jitter(Duration.ofMinutes(1), -1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentCacheTtlPolicy.jitter(Duration.ofMinutes(1), 51));

        log.info("统一 TTL 非法配置拒绝验证完成");
    }
}
