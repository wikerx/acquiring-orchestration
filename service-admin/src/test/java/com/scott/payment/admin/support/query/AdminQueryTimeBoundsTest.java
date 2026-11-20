package com.scott.payment.admin.support.query;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 固定时钟验证历史时间裁剪、空窗口及未来计划日期的不同语义。
 */
class AdminQueryTimeBoundsTest {
    private final AdminQueryTimeBounds bounds = bounds();

    @Test
    void timestampsShouldClampToRegisteredQuarterAndCurrentShanghaiTime() {
        var range = bounds.transactionTimes(LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2027, 2, 1, 0, 0));
        assertThat(range.begin()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(range.end()).isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 41, 28));
    }

    @Test
    void businessDatesShouldUseStorageTimezoneWithoutRejectingLongHistory() {
        var range = bounds.historicalDates(LocalDate.of(2026, 6, 1), LocalDate.of(2027, 2, 1));
        assertThat(range.begin()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void windowsOutsideHistoryShouldStayEmptyInsteadOfBecomingAnotherDay() {
        assertThat(bounds.historicalDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)).empty()).isTrue();
        assertThat(bounds.transactionTimes(LocalDateTime.of(2027, 1, 1, 0, 0),
                LocalDateTime.of(2027, 2, 1, 0, 0)).empty()).isTrue();
    }

    @Test
    void optionalScheduledDatesShouldRetainFutureReleaseWindow() {
        bounds.validateScheduledDates(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        assertThat(bounds.transactionTimes(null, null).begin()).isNull();
        assertThatThrownBy(() -> bounds.transactionTimes(LocalDateTime.of(2026, 8, 1, 0, 0), null))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> bounds.historicalDates(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1)))
                .isInstanceOf(ServiceException.class);
    }

    private static AdminQueryTimeBounds bounds() {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202604", "202603", "202701"));
        return new AdminQueryTimeBounds(properties,
                Clock.fixed(Instant.parse("2026-11-20T01:41:28Z"), ZoneOffset.UTC));
    }
}
