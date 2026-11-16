package com.scott.payment.clearing.support;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证清分无时区持久化时间与 MQ 绝对投递时间使用同一平台时区契约。
 */
class ClearingTimeSupportTest {

    /**
     * UTC 绝对时刻必须转换为同一时刻的 Asia/Shanghai 墙钟值。
     */
    @Test
    void shouldConvertAbsoluteTimeToPlatformLocalDateTime() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T08:39:18.175Z"), ZoneOffset.UTC);

        LocalDateTime result = ClearingTimeSupport.now(clock);

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 11, 3, 16, 39, 18, 175_000_000));
    }

    /**
     * 平台本地到期时间转换为 Instant 后必须保持同一绝对时刻并可无损往返。
     */
    @Test
    void shouldRoundTripScheduledPlatformTimeAcrossQuarterBoundary() {
        LocalDateTime localDateTime = LocalDateTime.of(2027, 1, 1, 0, 0, 0, 123_000_000);

        Instant instant = ClearingTimeSupport.toInstant(localDateTime);

        assertThat(instant).isEqualTo(Instant.parse("2026-12-31T16:00:00.123Z"));
        assertThat(ClearingTimeSupport.fromInstant(instant)).isEqualTo(localDateTime);
    }

    /**
     * 共享交易 Outbox 的 UTC 墙钟值必须能由 Payment Relay 还原为同一绝对时刻。
     */
    @Test
    void shouldConvertPlatformTimeToSharedOutboxUtcDateTime() {
        LocalDateTime localDateTime = LocalDateTime.of(2027, 1, 1, 0, 0, 0, 123_000_000);

        LocalDateTime utcDateTime = ClearingTimeSupport.toUtcLocalDateTime(localDateTime);

        assertThat(utcDateTime).isEqualTo(LocalDateTime.of(2026, 12, 31, 16, 0, 0, 123_000_000));
        assertThat(utcDateTime.toInstant(ZoneOffset.UTC))
                .isEqualTo(ClearingTimeSupport.toInstant(localDateTime));
    }
}
