package com.scott.payment.clearing.support;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 统一清分持久化时间与绝对投递时间之间的转换口径。
 *
 * <p>清分表和共享交易表的普通无时区 {@link LocalDateTime} 字段使用交易路由时区；
 * MQ 到期时间使用 {@link Instant} 表达绝对时刻。显式命名为 transactionUtcTime 的字段，
 * 以及共享交易 Outbox 的 deliverAt 字段，继续保存 UTC 墙钟值。</p>
 */
public final class ClearingTimeSupport {

    /**
     * 与交易分片、MySQL 会话和管理端查询一致的平台业务时区。
     */
    public static final ZoneId PLATFORM_ZONE_ID =
            ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID);

    /**
     * 工具类不允许实例化。
     */
    private ClearingTimeSupport() {
    }

    /**
     * 将可注入时钟的当前绝对时刻转换为平台本地时间。
     *
     * @param clock 只提供当前绝对时刻的时钟，时钟自身 zone 不影响结果
     * @return Asia/Shanghai 平台本地时间
     */
    public static LocalDateTime now(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clearing clock is required");
        }
        return fromInstant(clock.instant());
    }

    /**
     * 将绝对时刻转换为平台本地时间，用于无时区 DATETIME(3) 字段。
     *
     * @param instant 绝对时刻
     * @return Asia/Shanghai 平台本地时间
     */
    public static LocalDateTime fromInstant(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("clearing instant is required");
        }
        return LocalDateTime.ofInstant(instant, PLATFORM_ZONE_ID);
    }

    /**
     * 将平台本地时间转换为 MQ 调度使用的绝对时刻。
     *
     * @param localDateTime Asia/Shanghai 平台本地时间
     * @return 对应的绝对时刻
     */
    public static Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            throw new IllegalArgumentException("clearing local date time is required");
        }
        return localDateTime.atZone(PLATFORM_ZONE_ID).toInstant();
    }

    /**
     * 将平台本地时间转换为共享交易 Outbox 约定的 UTC 墙钟值。
     *
     * <p>{@code transaction_event_outbox.deliver_at} 使用无时区 DATETIME(3) 保存 UTC，
     * Payment Relay 会按 UTC 将其还原为 Broker 绝对投递时间。</p>
     *
     * @param localDateTime Asia/Shanghai 平台本地时间
     * @return 表达同一绝对时刻的 UTC LocalDateTime
     */
    public static LocalDateTime toUtcLocalDateTime(LocalDateTime localDateTime) {
        return LocalDateTime.ofInstant(toInstant(localDateTime), ZoneOffset.UTC);
    }
}
