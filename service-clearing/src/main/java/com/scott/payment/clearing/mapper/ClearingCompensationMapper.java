package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingCompensationCandidateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 清分补偿候选只读扫描 Mapper。 */
public interface ClearingCompensationMapper {

    /** 在单季度半开窗口内按动作时间和物理主键游标扫描恢复候选。 */
    @Select("""
            <script>
            SELECT o.id AS operation_row_id,
                   o.transaction_id, o.operation_id, o.merchant_id, o.merchant_order_no,
                   o.source_transaction_id, o.transaction_type, o.transaction_status,
                   o.label_currency, o.label_amount, o.approved_currency, o.approved_amount,
                   o.transaction_currency, o.transaction_amount, o.currency_exponent,
                   o.transaction_date_time, o.transaction_utc_time, o.transaction_time_zone,
                   o.version AS operation_version, o.clearing_status AS operation_clearing_status,
                   f.finance_state_id, f.clearing_status, f.clearing_revision,
                   f.clearing_retry_count, f.next_retry_time, f.last_failure_code,
                   f.processing_deadline, f.version AS finance_state_version,
                   CASE
                     WHEN f.id IS NULL THEN 'MISSING_FINANCE_STATE'
                     WHEN f.clearing_status = 'NOT_CLEARED' THEN 'NOT_CLEARED'
                     WHEN f.clearing_status = 'PENDING'
                          AND COALESCE(f.clearing_request_time, f.create_time) &lt;= #{pendingBefore}
                       THEN 'PENDING_TIMEOUT'
                     WHEN f.clearing_status = 'PROCESSING' AND f.processing_deadline &lt;= #{now}
                       THEN 'PROCESSING_TIMEOUT'
                     WHEN f.clearing_status = 'FAILED' AND f.next_retry_time &lt;= #{now}
                       THEN 'FAILED_DUE'
                     WHEN f.clearing_status = 'WAITING_SOURCE' AND f.next_retry_time &lt;= #{now}
                       THEN 'WAITING_SOURCE_DUE'
                     ELSE 'PROJECTION_MISMATCH'
                   END AS reason
            FROM transaction_operation o
            LEFT JOIN transaction_finance_state f
              ON f.transaction_id = o.transaction_id
             AND f.transaction_date_time = o.transaction_date_time
             AND f.deleted = 0
            WHERE o.transaction_date_time &gt;= #{beginTime}
              AND o.transaction_date_time &lt; #{endTime}
              AND o.transaction_status IN ('SUCCESS', 'FAILED')
              AND o.deleted = 0
              AND (
                   f.id IS NULL
                   OR f.clearing_status = 'NOT_CLEARED'
                   OR (f.clearing_status = 'PENDING'
                       AND COALESCE(f.clearing_request_time, f.create_time) &lt;= #{pendingBefore})
                   OR (f.clearing_status = 'PROCESSING' AND f.processing_deadline &lt;= #{now})
                   OR (f.clearing_status IN ('FAILED', 'WAITING_SOURCE') AND f.next_retry_time &lt;= #{now})
                   OR (f.clearing_status IN ('CLEARED', 'NOT_REQUIRED')
                       AND o.clearing_status &lt;&gt; f.clearing_status)
                  )
              <if test='cursorTransactionDateTime != null and cursorId != null'>
                AND (o.transaction_date_time &gt; #{cursorTransactionDateTime}
                     OR (o.transaction_date_time = #{cursorTransactionDateTime} AND o.id &gt; #{cursorId}))
              </if>
            ORDER BY o.transaction_date_time ASC, o.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<ClearingCompensationCandidateDO> selectCandidates(
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("cursorTransactionDateTime") LocalDateTime cursorTransactionDateTime,
            @Param("cursorId") Long cursorId,
            @Param("pendingBefore") LocalDateTime pendingBefore,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);
}
