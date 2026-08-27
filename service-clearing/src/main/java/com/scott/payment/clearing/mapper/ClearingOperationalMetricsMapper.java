package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingPendingMetricsDO;
import com.scott.payment.clearing.entity.ClearingReserveRemainingMetricsDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 清分运维指标只读 Mapper；每次查询必须携带单季度半开时间窗口。 */
public interface ClearingOperationalMetricsMapper {

    /** 按清分状态聚合单季度待处理数量和最老等待秒数。 */
    @Select("""
            SELECT clearing_status,
                   COUNT(*) AS pending_count,
                   GREATEST(TIMESTAMPDIFF(
                       SECOND,
                       MIN(COALESCE(clearing_request_time, create_time)),
                       #{now}), 0) AS oldest_pending_seconds
            FROM transaction_finance_state
            WHERE transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTime}
              AND deleted = 0
              AND clearing_status IN (
                  'NOT_CLEARED', 'PENDING', 'PROCESSING',
                  'WAITING_SOURCE', 'FAILED', 'MANUAL_REVIEW'
              )
            GROUP BY clearing_status
            """)
    List<ClearingPendingMetricsDO> selectPendingByStatus(
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("now") LocalDateTime now);

    /** 按标签币种聚合单季度尚未返还或释放的保证金负债。 */
    @Select("""
            SELECT reserve_currency,
                   SUM(remaining_amount) AS remaining_amount
            FROM transaction_reserve_clearing_state
            WHERE transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTime}
              AND remaining_amount > 0
            GROUP BY reserve_currency
            """)
    List<ClearingReserveRemainingMetricsDO> selectReserveRemainingByCurrency(
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime);
}
