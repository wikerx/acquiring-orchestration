package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingReserveAdjustmentDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 保证金调整审批 Mapper；申请只追加，批准或拒绝只允许从 PENDING_REVIEW 版本 CAS。 */
public interface ClearingReserveAdjustmentMapper {

    @Insert("""
            INSERT INTO clearing_reserve_adjustment
            (adjustment_no, request_key, reserve_state_id, original_transaction_id,
             original_transaction_date_time, merchant_id, reserve_currency,
             reserve_currency_exponent, direction, adjustment_amount, requested_release_date,
             expected_reserve_state_version, reason, submit_operator, adjustment_status,
             version, create_time, update_time)
            VALUES
            (#{row.adjustmentNo}, #{row.requestKey}, #{row.reserveStateId},
             #{row.originalTransactionId}, #{row.originalTransactionDateTime}, #{row.merchantId},
             #{row.reserveCurrency}, #{row.reserveCurrencyExponent}, #{row.direction},
             #{row.adjustmentAmount}, #{row.requestedReleaseDate},
             #{row.expectedReserveStateVersion}, #{row.reason}, #{row.submitOperator},
             #{row.adjustmentStatus}, #{row.version}, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") ClearingReserveAdjustmentDO row);

    @Select("""
            SELECT * FROM clearing_reserve_adjustment
            WHERE request_key = #{requestKey}
            LIMIT 1 FOR UPDATE
            """)
    ClearingReserveAdjustmentDO selectByRequestKeyForUpdate(@Param("requestKey") String requestKey);

    @Select("""
            SELECT * FROM clearing_reserve_adjustment
            WHERE adjustment_no = #{adjustmentNo}
            LIMIT 1 FOR UPDATE
            """)
    ClearingReserveAdjustmentDO selectForUpdate(@Param("adjustmentNo") String adjustmentNo);

    @Update("""
            UPDATE clearing_reserve_adjustment
            SET adjustment_status = 'REJECTED', review_operator = #{reviewOperator},
                review_comment = #{reviewComment}, review_time = #{reviewTime},
                version = version + 1, update_time = #{reviewTime}
            WHERE adjustment_no = #{adjustmentNo}
              AND adjustment_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion}
            """)
    int markRejected(@Param("adjustmentNo") String adjustmentNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("reviewOperator") String reviewOperator,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewTime") LocalDateTime reviewTime);

    @Update("""
            UPDATE clearing_reserve_adjustment
            SET adjustment_status = 'EXECUTED', review_operator = #{reviewOperator},
                review_comment = #{reviewComment}, review_time = #{reviewTime},
                execution_transaction_id = #{executionTransactionId},
                source_revision = #{sourceRevision}, executed_time = #{executedTime},
                version = version + 1, update_time = #{executedTime}
            WHERE adjustment_no = #{adjustmentNo}
              AND adjustment_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion}
            """)
    int markExecuted(@Param("adjustmentNo") String adjustmentNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("reviewOperator") String reviewOperator,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewTime") LocalDateTime reviewTime,
                     @Param("executionTransactionId") String executionTransactionId,
                     @Param("sourceRevision") int sourceRevision,
                     @Param("executedTime") LocalDateTime executedTime);
}
