package com.scott.payment.component.db.sharding;

import com.google.common.collect.Range;
import com.scott.payment.component.core.exception.TransactionDataUnavailableException;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : QuarterTableShardingAlgorithmTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 验证季度算法的精确、范围、边界、缺节点与 JVM 默认时区无关性。
 * @status : create
 */
class QuarterTableShardingAlgorithmTest {

    private static final List<String> TARGETS = List.of(
            "transaction_order_202601",
            "transaction_order_202602",
            "transaction_order_202603",
            "transaction_order_202604",
            "transaction_order_202701");

    @Test
    void shouldRouteEveryQuarterExactly() {
        QuarterTableShardingAlgorithm algorithm = algorithm();

        assertEquals("transaction_order_202601", precise(algorithm, LocalDateTime.of(2026, 3, 31, 23, 59, 59)));
        assertEquals("transaction_order_202602", precise(algorithm, LocalDateTime.of(2026, 4, 1, 0, 0)));
        assertEquals("transaction_order_202603", precise(algorithm, LocalDateTime.of(2026, 9, 30, 23, 59, 59, 999_000_000)));
        assertEquals("transaction_order_202604", precise(algorithm, LocalDateTime.of(2026, 10, 1, 0, 0)));
        assertEquals("transaction_order_202701", precise(algorithm, LocalDateTime.of(2027, 1, 1, 0, 0)));
    }

    @Test
    void shouldUseAsiaShanghaiForInstantRegardlessOfJvmDefaultZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            String utcDefault = precise(algorithm(), Instant.parse("2026-06-30T16:00:00Z"));
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            String newYorkDefault = precise(algorithm(), Instant.parse("2026-06-30T16:00:00Z"));

            assertEquals("transaction_order_202603", utcDefault);
            assertEquals(utcDefault, newYorkDefault);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void shouldRespectOpenQuarterBoundaryAndCrossYearRange() {
        QuarterTableShardingAlgorithm algorithm = algorithm();

        Collection<String> closedOpen = range(algorithm, Range.closedOpen(
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)));
        Collection<String> crossYear = range(algorithm, Range.closedOpen(
                LocalDateTime.of(2026, 12, 31, 23, 59, 59, 999_000_000),
                LocalDateTime.of(2027, 1, 1, 0, 0, 0, 1)));

        assertEquals(List.of("transaction_order_202603"), closedOpen);
        assertEquals(List.of("transaction_order_202604", "transaction_order_202701"), crossYear);
    }

    @Test
    void shouldRejectNullUnsupportedTypeAndMissingNode() {
        QuarterTableShardingAlgorithm algorithm = algorithm();

        assertThrows(IllegalArgumentException.class, () -> precise(algorithm, null));
        assertThrows(IllegalArgumentException.class, () -> precise(algorithm, "2026-08-01"));
        TransactionDataUnavailableException preciseFailure = assertThrows(TransactionDataUnavailableException.class,
                () -> precise(algorithm, LocalDateTime.of(2028, 1, 1, 0, 0)));
        TransactionDataUnavailableException rangeFailure = assertThrows(TransactionDataUnavailableException.class,
                () -> range(algorithm, Range.closedOpen(
                LocalDateTime.of(2025, 10, 1, 0, 0),
                LocalDateTime.of(2026, 4, 1, 0, 0))));

        assertEquals("2028-Q1", preciseFailure.getQuarter());
        assertEquals("2025-Q4", rangeFailure.getQuarter());
    }

    private QuarterTableShardingAlgorithm algorithm() {
        QuarterTableShardingAlgorithm algorithm = new QuarterTableShardingAlgorithm();
        Properties properties = new Properties();
        properties.setProperty("database-zone-id", "Asia/Shanghai");
        properties.setProperty("rule-version", "test-001");
        algorithm.init(properties);
        return algorithm;
    }

    private String precise(QuarterTableShardingAlgorithm algorithm, Comparable<?> value) {
        return algorithm.doSharding(TARGETS,
                new PreciseShardingValue<>("transaction_order", "transaction_date_time", null, value));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Collection<String> range(QuarterTableShardingAlgorithm algorithm, Range<? extends Comparable<?>> range) {
        return algorithm.doSharding(TARGETS,
                new RangeShardingValue("transaction_order", "transaction_date_time", null, (Range) range));
    }
}
