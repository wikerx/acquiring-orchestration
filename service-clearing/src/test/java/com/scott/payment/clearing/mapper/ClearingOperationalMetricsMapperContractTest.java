package com.scott.payment.clearing.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingOperationalMetricsMapperContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证运维聚合 SQL 只读且始终携带单季度半开分片范围。
 * @status : create
 */
class ClearingOperationalMetricsMapperContractTest {

    @Test
    void metricsQueriesShouldBeReadOnlyAndShardBounded() throws Exception {
        assertReadOnlyQuarterQuery("selectPendingByStatus",
                LocalDateTime.class, LocalDateTime.class, LocalDateTime.class);
        assertReadOnlyQuarterQuery("selectReserveRemainingByCurrency",
                LocalDateTime.class, LocalDateTime.class);
    }

    /** 保证金负债 Gauge 必须覆盖所有正余额状态，不能漏掉未来调整态中的剩余负债。 */
    @Test
    void reserveMetricsShouldAggregateEveryPositiveRemainingBalance() throws Exception {
        Method method = ClearingOperationalMetricsMapper.class.getMethod(
                "selectReserveRemainingByCurrency", LocalDateTime.class, LocalDateTime.class);
        String sql = String.join("\n", method.getAnnotation(Select.class).value()).toUpperCase();

        assertThat(sql).contains("REMAINING_AMOUNT > 0", "GROUP BY RESERVE_CURRENCY")
                .doesNotContain("RESERVE_STATUS = 'OPEN'");
    }

    private void assertReadOnlyQuarterQuery(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ClearingOperationalMetricsMapper.class.getMethod(methodName, parameterTypes);
        String sql = String.join("\n", method.getAnnotation(Select.class).value()).toUpperCase();

        assertThat(sql).contains("SELECT", "TRANSACTION_DATE_TIME >= #{BEGINTIME}",
                "TRANSACTION_DATE_TIME < #{ENDTIME}");
        assertThat(sql).doesNotContain(" INSERT ", " UPDATE ", " DELETE ", " ALTER ", " DROP ", " TRUNCATE ");
    }
}
